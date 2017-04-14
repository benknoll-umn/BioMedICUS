/*
 * Copyright (c) 2017 Regents of the University of Minnesota.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umn.biomedicus.common.labels;

import edu.umn.biomedicus.common.types.text.Document;
import edu.umn.biomedicus.common.types.text.Span;
import edu.umn.biomedicus.common.types.text.TextLocation;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.PatternSyntaxException;

/**
 *
 */
public class Searcher {
    static final Node ACCEPT = new Node() {
        @Nullable
        @Override
        Class<?> firstType() {
            return null;
        }

        @Override
        void swapNext(Node next) {
            throw new IllegalStateException(
                    "Should never swap next on the accept node");
        }
    };

    static final int LOOP_LIMIT = 10_000;

    private final Node root;
    private final Node searchRoot;
    private final int numberGroups;
    private final int numberLocals;
    private final Map<String, Integer> groupNames;

    Searcher(Node root,
             Node searchRoot,
             int numberGroups,
             int numberLocals,
             Map<String, Integer> groupNames) {
        this.root = root;
        this.searchRoot = searchRoot;
        this.numberGroups = numberGroups;
        this.numberLocals = numberLocals;
        this.groupNames = groupNames;
    }

    public static Searcher parse(LabelAliases labelAliases, String pattern) {
        return new Parser(labelAliases, pattern).compile();
    }

    public Search createSearcher(Document document) {
        return new DefaultSearch(document, document.getDocumentSpan());
    }

    public Search createSearcher(Document document, Span span) {
        return new DefaultSearch(document, span);
    }

    /**
     * Represents a return value of an atomic chain with a head and a tail. The
     * begin value after the tail is the proper matching begin value for the
     * entire chain.
     */
    static class Chain {
        final Node head;
        final Node tail;

        Chain(Node single) {
            head = tail = single;
        }

        Chain(Node head, Node tail) {
            this.head = head;
            this.tail = tail;
        }
    }

    private static class Parser {
        private final LabelAliases labelAliases;
        private final String pattern;
        private final char[] arr;
        private final Map<String, Integer> groupNames = new HashMap<>();
        private final Map<Integer, Class<?>> groupTypes = new HashMap<>();
        private int index = 0;
        private int groupIndex = 0;
        private int localsCount = 0;

        private Parser(LabelAliases labelAliases,
                       String pattern) {
            this.labelAliases = labelAliases;
            this.pattern = pattern;
            char[] charr = pattern.toCharArray();
            char[] tmp = new char[charr.length + 2];
            tmp[tmp.length - 1] = 0;
            tmp[tmp.length - 2] = 0;
            System.arraycopy(charr, 0, tmp, 0, charr.length);
            arr = tmp;
        }

        private Searcher compile() {
            LoadBegin end = new LoadBegin(localsCount++);
            Node root = alts(end);
            Class<?> aClass = root.firstType();

            Node searchRoot;
            if (aClass == null) {
                searchRoot = new CharacterStepping();
                searchRoot.swapNext(root);
            } else {
                Node join = new Noop();
                join.swapNext(new SaveBegin(end.local));
                join.next.swapNext(root);
                Branch branch = new Branch();
                TypeMatch typeMatch = new TypeMatch(aClass, true,
                        true, -1);
                typeMatch.swapNext(join);
                branch.add(join);
                branch.add(typeMatch);
                searchRoot = branch;
            }

            return new Searcher(root, searchRoot, groupIndex, localsCount,
                    groupNames);
        }

        private Node alts(Node end) {
            Chain concat = concat(end);
            peekPastWhiteSpace();
            if (!accept('|')) {
                return concat.head;
            }
            Node join = new Noop();
            join.swapNext(end);
            Branch branch = new Branch();
            if (concat.head == end) {
                branch.add(join);
            } else {
                branch.add(concat.head);
                concat.tail.swapNext(join);
            }
            do {
                Chain next = concat(join);
                branch.add(next.head);
                peekPastWhiteSpace();
            } while (accept('|'));
            return branch;
        }

        private Chain concat(Node end) {
            Node head = null;
            Node tail = null;
            Node node;
            LOOP:
            while (true) {
                int ch = peekPastWhiteSpace();
                switch (ch) {
                    case '(':
                    case '[':
                        Chain chain = ch == '(' ? group() : pinning();
                        Chain repetition = groupRepetition(chain.head,
                                chain.tail);

                        if (head == null) {
                            head = repetition.head;
                            tail = repetition.tail;
                        } else {
                            tail.next = repetition.head;
                            tail = repetition.tail;
                        }

                        continue;
                    case '|':
                    case ')':
                    case ']':
                    case '&':
                    case 0:
                        break LOOP;
                    default:
                        node = type(false);
                }

                node = atomicRepetition(node);

                if (head == null) {
                    head = tail = node;
                } else {
                    tail.swapNext(node);
                    tail = node;
                }
            }
            if (head == null) {
                return new Chain(end, end);
            }
            if (head != tail) {
                Node tmp = head.next;
                int local = localsCount++;
                head.swapNext(new SaveBegin(local));
                head.next.swapNext(tmp);

                LoadBegin loadBegin = new LoadBegin(local);
                tail.swapNext(loadBegin);
                tail = loadBegin;
            }

            tail.swapNext(end);
            return new Chain(head, tail);
        }

        private Chain groupRepetition(Node head, Node tail) {
            int ch = peek();
            switch (ch) {
                case '?':
                    ch = next();
                    if (ch == '+') {
                        next();
                        head = new PossessiveOptional(head);
                        return new Chain(head, head);
                    } else {
                        tail.swapNext(new Noop());
                        tail = tail.next;
                        Branch branch = new Branch();
                        if (ch == '?') {
                            next();
                            branch.add(tail);
                            branch.add(head);
                        } else {
                            branch.add(head);
                            branch.add(tail);
                        }
                        head = branch;
                        return new Chain(head, tail);
                    }
                case '*':
                    ch = next();
                    RecursiveLoop recursiveLoop;
                    if (ch == '+') {
                        next();
                        recursiveLoop = new PossessiveLoop(head, localsCount++,
                                localsCount++, 0, LOOP_LIMIT);
                    } else if (ch == '?') {
                        next();
                        recursiveLoop = new LazyLoop(head, localsCount++,
                                localsCount++, 0, LOOP_LIMIT);
                    } else {
                        recursiveLoop = new GreedyLoop(head, localsCount++,
                                localsCount++, 0, LOOP_LIMIT);
                    }
                    tail.next = recursiveLoop;
                    return new Chain(new LoopHead(recursiveLoop,
                            recursiveLoop.beginLocal), recursiveLoop);
                case '+':
                    ch = next();
                    if (ch == '+') {
                        next();
                        recursiveLoop = new PossessiveLoop(head, localsCount++,
                                localsCount++, 1, LOOP_LIMIT);
                    } else if (ch == '?') {
                        next();
                        recursiveLoop = new LazyLoop(head, localsCount++,
                                localsCount++, 1, LOOP_LIMIT);
                    } else {
                        recursiveLoop = new GreedyLoop(head, localsCount++,
                                localsCount++, 1, LOOP_LIMIT);
                    }
                    tail.next = recursiveLoop;
                    return new Chain(new LoopHead(recursiveLoop,
                            recursiveLoop.beginLocal), recursiveLoop);

                case '{':
                    Span span = parseCurlyRange();
                    int min = span.getBegin();
                    int max = span.getEnd();

                    ch = peek();
                    if (ch == '+') {
                        next();
                        recursiveLoop = new PossessiveLoop(head, localsCount++,
                                localsCount++, min, max);
                    } else if (ch == '?') {
                        next();
                        recursiveLoop = new LazyLoop(head, localsCount++,
                                localsCount++, min, max);
                    } else {
                        recursiveLoop = new GreedyLoop(head, localsCount++,
                                localsCount++, min, max);
                    }
                    tail.next = recursiveLoop;
                    return new Chain(new LoopHead(recursiveLoop,
                            recursiveLoop.beginLocal), recursiveLoop);

                default:
                    return new Chain(head, tail);
            }
        }

        private Node atomicRepetition(Node node) {
            int ch = peek();
            switch (ch) {
                case '?':
                    ch = next();
                    if (ch == '?') {
                        next();
                        node = new LazyOptional(node);
                    } else if (ch == '+') {
                        next();
                        node = new PossessiveOptional(node);
                    } else {
                        node = new GreedyOptional(node);
                    }
                    break;
                case '*':
                    ch = next();
                    RecursiveLoop loop;
                    if (ch == '+') {
                        next();
                        loop = new PossessiveLoop(node, localsCount++,
                                localsCount++, 0, LOOP_LIMIT);
                    } else if (ch == '?') {
                        next();
                        loop = new LazyLoop(node, localsCount++,
                                localsCount++, 0, LOOP_LIMIT);
                    } else {
                        loop = new GreedyLoop(node, localsCount++,
                                localsCount++, 0, LOOP_LIMIT);
                    }
                    return new LoopHead(loop, loop.beginLocal);
                case '+':
                    ch = next();
                    if (ch == '+') {
                        next();
                        loop = new PossessiveLoop(node, localsCount++,
                                localsCount++, 1, LOOP_LIMIT);
                    } else if (ch == '?') {
                        next();
                        loop = new LazyLoop(node, localsCount++,
                                localsCount++, 1, LOOP_LIMIT);
                    } else {
                        loop = new GreedyLoop(node, localsCount++,
                                localsCount++, 1, LOOP_LIMIT);
                    }
                    return new LoopHead(loop, loop.beginLocal);

                case '{':
                    Span span = parseCurlyRange();
                    int min = span.getBegin();
                    int max = span.getEnd();

                    ch = peek();
                    if (ch == '+') {
                        next();
                        loop = new PossessiveLoop(node, localsCount++,
                                localsCount++, min, max);
                    } else if (ch == '?') {
                        next();
                        loop = new LazyLoop(node, localsCount++,
                                localsCount++, min, max);
                    } else {
                        loop = new GreedyLoop(node, localsCount++,
                                localsCount++, min, max);
                    }
                    return new LoopHead(loop, loop.beginLocal);
            }
            return node;
        }

        private Span parseCurlyRange() {
            int ch;
            ch = peekNext();
            if (!Character.isDigit(ch)) {
                throw error(
                        "Curly brackets should be in format {min[,max]}");
            }
            skip();
            int min = 0;
            do {
                min = min * 10 + (ch - '0');
            } while (ch <= '9' && ch >= '0');
            int max = min;
            if (ch == ',') {
                ch = read();
                max = LOOP_LIMIT;
                if (ch != '}') {
                    max = 0;
                    while (ch <= '9' && ch >= '0') {
                        max = max * 10 + (ch - '0');
                        ch = read();
                    }
                }
            }
            if (ch != '}') {
                throw error(
                        "Unclosed curly bracket repetition");
            }
            if (max < min || min < 0 || max < 0) {
                throw error(
                        "Curly bracket repetition illegal range");
            }
            return new Span(min, max);
        }

        private Chain group() {
            Node head;
            Node tail;

            int ch = next();
            if (ch == '?') {
                ch = skip();
                switch (ch) {
                    case '=':
                    case '!':
                        tail = createGroupTail();
                        head = alts(tail);
                        if (ch == '=') {
                            head = tail = new PositiveLookahead(head);
                        } else {
                            head = tail = new NegativeLookahead(head);
                        }
                        break;
                    case '>':
                        tail = createGroupTail();
                        head = alts(tail);
                        head = tail = new Independent(head);
                        break;
                    case '<':
                        String name = readGroupName();
                        if (groupNames.containsKey(name)) {
                            throw error("Duplicate capturing group name: \""
                                    + name + "\"");
                        }
                        GroupTail groupTail = createGroupTail();
                        tail = groupTail;
                        groupNames.put(name, groupTail.groupIndex);
                        head = alts(tail);
                        break;
                    default:
                        unread();
                        tail = createGroupTail();
                        head = alts(tail);
                        break;
                }
            } else {
                tail = createGroupTail();
                head = alts(tail);
            }
            peekPastWhiteSpace();
            expect(')', "Unclosed group");

            return new Chain(head, tail);
        }

        @NotNull
        private GroupTail createGroupTail() {
            GroupTail tail = new GroupTail(groupIndex);
            groupIndex = groupIndex + 2;
            return tail;
        }

        private String readGroupName() {
            StringBuilder stringBuilder = new StringBuilder();
            int ch;
            while ((ch = read()) != '>') {
                if (!Character.isAlphabetic(ch) && !Character.isDigit(ch)) {
                    throw error(
                            "Non alphanumeric character in capturing group name");
                }
                stringBuilder.append((char) ch);
            }

            if (stringBuilder.length() == 0) {
                throw error("0-length named capturing gorup");
            }

            return stringBuilder.toString();
        }

        private Chain pinning() {
            next();
            int ch = peekPastWhiteSpace();
            boolean seek = false;
            boolean covered = false;
            if (ch == '?') {
                seek = true;
                read();
            }
            if (ch == '!') {
                covered = true;
                read();
            }

            Node pinned = type(seek);

            if (accept(']')) {
                return new Chain(pinned);
            }

            InnerConditions innerConditions
                    = new InnerConditions(localsCount++, covered);

            do {
                Node inner = alts(ACCEPT);
                innerConditions.addCondition(inner);
                peekPastWhiteSpace();
            } while (accept('&'));

            expect(']', "Unclosed pinning group");

            pinned.swapNext(innerConditions);
            return new Chain(pinned, innerConditions);
        }

        private Node type(boolean seek) {

            int ch = read();
            if (!Character.isAlphabetic(ch) && !Character.isDigit(ch))
                throw error("Illegal identifier");

            String first = readAlphanumeric(ch);
            String variable = null;
            String type;
            ch = peek();
            if (ch == ':') {
                next();
                ch = read();
                variable = first;
                type = readAlphanumeric(ch);
            } else {
                type = first;
            }
            Class<?> aClass = labelAliases.getLabelable(type);
            if (aClass == null) {
                try {
                    aClass = Class.forName(type);
                } catch (ClassNotFoundException e) {
                    throw error("Couldn't find a type with alias or name "
                            + type);
                }
            }
            int group = -1;
            if (variable != null) {
                group = groupIndex;
                groupTypes.put(group, aClass);
                groupNames.put(variable, groupIndex);
                groupIndex = groupIndex + 2;
            }
            ch = peek();
            if (ch == '=') {
                next();
                ch = read();
                String enumValue = readAlphanumeric(ch);
                Enum t = Enum.valueOf((Class<Enum>) aClass, enumValue);
                return new EnumMatch(aClass, seek, false, group,
                        t);
            } else {
                TypeMatch node = new TypeMatch(aClass, seek, false, group);
                if (ch == '{') {
                    next();
                    do {
                        parseProperty(node);
                    } while (consumePastWhiteSpace(','));
                    peekPastWhiteSpace();
                    expect('}', "Unclosed properties group");
                }

                return node;
            }
        }

        String readAlphanumeric(int ch) {
            StringBuilder groupName = new StringBuilder();
            groupName.append((char) ch);
            while (Character.isAlphabetic(ch = peek())
                    || Character.isDigit(ch)) {
                groupName.append((char) ch);
                read();
            }
            return groupName.toString();
        }

        String parseTypeName(int ch) {
            StringBuilder typeName = new StringBuilder();
            typeName.append((char) ch);
            read();
            while (Character.isAlphabetic(ch = peek())
                    || Character.isDigit(ch)) {
                typeName.append((char) ch);
            }

            return typeName.toString();
        }

        String parsePropertyStringValue() {
            StringBuilder vb = new StringBuilder();
            int ch;
            boolean escaped = false;
            while ((ch = read()) != '"' || escaped) {
                if (escaped) {
                    escaped = false;
                    vb.append((char) ch);
                } else if (ch == '\\') {
                    escaped = true;
                } else {
                    vb.append((char) ch);
                }
            }
            return vb.toString();
        }

        Object parseNumber(int ch) {
            StringBuilder nb = new StringBuilder();
            nb.append((char) ch);
            boolean isDouble = false;
            while (Character.isDigit(ch = peek()) || (!isDouble && ch == '.')) {
                if (ch == '.') {
                    isDouble = true;
                }
                nb.append((char) ch);
                read();
            }

            String digitString = nb.toString();
            if (isDouble) {
                return Double.parseDouble(digitString);
            } else {
                return Long.parseLong(digitString);
            }
        }

        String parseBackreferenceGroupName() {
            StringBuilder bsb = new StringBuilder();
            read();
            int ch;
            while (Character.isAlphabetic(ch = peek())
                    || Character.isDigit(ch)) {
                bsb.append(ch);
                read();
            }
            return bsb.toString();
        }

        void parseProperty(TypeMatch typeMatch) {
            StringBuilder pnsb = new StringBuilder();
            int ch;
            while (Character.isAlphabetic(ch = read()) || Character.isDigit(ch))
                pnsb.append((char) ch);
            if (ch != '=') throw error("Invalid property value format");
            String propertyName = pnsb.toString();
            ch = read();
            if (ch == '"') {
                typeMatch.addPropertyMatch(propertyName,
                        parsePropertyStringValue());
            } else if (Character.isDigit(ch)) {
                typeMatch.addNumberPropertyMatch(propertyName, parseNumber(ch));
            } else if (Character.isAlphabetic(ch)) {
                Object value;
                if (ch == 't' || ch == 'T' || ch == 'y' || ch == 'Y') {
                    value = true;
                } else if (ch == 'f' || ch == 'F' || ch == 'n' || ch == 'N') {
                    value = false;
                } else {
                    throw error("Invalid property value");
                }
                while (Character.isAlphabetic(peek()))
                    read();
                typeMatch.addPropertyMatch(propertyName, value);
            } else if (ch == '$') {
                String backReferenceGroup = parseBackreferenceGroupName();
                ch = peek();
                if (ch == '.') {
                    Integer brGroup = groupNames.get(backReferenceGroup);
                    Class<?> type = groupTypes.get(brGroup);
                    String backPropertyName = parseTypeName(read());
                    try {
                        Method method = type.getMethod(backPropertyName);
                        typeMatch.addPropertyValueBackReference(propertyName,
                                backReferenceGroup, method);
                    } catch (NoSuchMethodException e) {
                        throw error(e.getLocalizedMessage());
                    }
                } else {
                    typeMatch.addSpanBackReference(propertyName,
                            backReferenceGroup);
                }
            } else {
                throw error("Illegal property value");
            }
        }

        PatternSyntaxException error(String desc) {
            return new PatternSyntaxException(desc, pattern, index);
        }

        boolean atEnd() {
            return index == arr.length;
        }

        int read() {
            return arr[index++];
        }

        int readPastWhitespace() {
            int ch;
            do {
                ch = read();
            } while (Character.isWhitespace(ch));
            return ch;
        }

        int peekPastWhiteSpace() {
            int ch;
            while (Character.isWhitespace(ch = peek())) {
                read();
            }
            return ch;
        }

        int peek() {
            return arr[index];
        }

        int peekNext() {
            return arr[index + 1];
        }

        int skip() {
            int ch = arr[index + 1];
            index += 2;
            return ch;
        }

        void unread() {
            index--;
        }

        int next() {
            return arr[++index];
        }

        boolean accept(int ch) {
            if (arr[index] == ch) {
                index = index + 1;
                return true;
            }
            return false;
        }

        boolean expect(int ch, String msg) {
            if (arr[index] != ch) {
                throw error(msg);
            }
            index = index + 1;
            return true;
        }

        boolean consumePastWhiteSpace(int ch) {
            int peek = peekPastWhiteSpace();
            if (peek == ch) {
                read();
                return true;
            } else {
                return false;
            }
        }
    }

    static class Node {
        protected Node next;

        Node() {
            next = ACCEPT;
        }

        State search(DefaultSearch search, State state) {
            return state;
        }

        @Nullable
        Class<?> firstType() {
            return next.firstType();
        }

        void swapNext(Node next) {
            this.next = next;
        }
    }

    static class CharacterStepping extends Node {
        @Override
        State search(DefaultSearch search, State state) {
            for (int i = state.end; i < state.limit; i++) {
                State res = next
                        .search(search, new State(i, i, state.limit));
                if (res.isHit()) {
                    return res;
                }
            }
            return State.miss();
        }
    }

    static class SaveBegin extends Node {
        final int local;

        SaveBegin(int local) {
            this.local = local;
        }

        @Override
        State search(DefaultSearch search, State state) {
            search.locals[local] = state.begin;
            return next.search(search, state);
        }
    }

    static class LoadBegin extends Node {
        final int local;

        LoadBegin(int local) {
            this.local = local;
        }

        @Override
        public State search(DefaultSearch search, State state) {
            return next.search(search, state.setBegin(search.locals[local]));
        }
    }

    static class GroupTail extends Node {
        final int groupIndex;

        GroupTail(int groupIndex) {
            this.groupIndex = groupIndex;
        }

        @Override
        State search(DefaultSearch search, State state) {
            search.groups[groupIndex] = state.begin;
            search.groups[groupIndex + 1] = state.end;
            return next.search(search, state);
        }
    }

    static class Branch extends Node {
        Node[] paths = new Node[2];
        int size = 0;

        void add(Node node) {
            if (size >= paths.length) {
                Node[] tmp = new Node[paths.length * 2];
                System.arraycopy(paths, 0, tmp, 0, paths.length);
                paths = tmp;
            }
            paths[size++] = node;
        }

        @Override
        public State search(DefaultSearch search, State state) {
            for (int i = 0; i < size; i++) {
                State res = paths[i].search(search, state);
                if (res.isHit()) {
                    return res;
                }
            }
            return State.miss();
        }
    }

    static class Noop extends Node {
        @Override
        public State search(DefaultSearch search, State state) {
            return next.search(search, state);
        }
    }

    static class PositiveLookahead extends Node {
        Node condition;

        PositiveLookahead(Node condition) {
            this.condition = condition;
        }

        @Override
        State search(DefaultSearch search, State state) {
            State res = condition.search(search, state);
            if (res.isMiss()) {
                return res;
            }
            return next.search(search, state);
        }
    }

    static class NegativeLookahead extends Node {
        Node condition;

        NegativeLookahead(Node condition) {
            this.condition = condition;
        }

        @Override
        State search(DefaultSearch search, State state) {
            State res = condition.search(search, state);
            if (res.isMiss()) {
                return res;
            }
            return next.search(search, state);
        }
    }

    static class Independent extends Node {
        final Node node;

        Independent(Node node) {
            this.node = node;
        }

        @Override
        State search(DefaultSearch search, State state) {
            State res = node.search(search, state);
            if (res.isHit()) {
                return next.search(search, res);
            } else {
                return res;
            }
        }
    }

    static class GreedyOptional extends Node {
        final Node option;

        GreedyOptional(Node option) {
            this.option = option;
        }

        @Override
        State search(DefaultSearch search, State state) {
            State res = option.search(search, state);
            if (res.isHit()) {
                State nextRes = next.search(search, res);
                if (nextRes.isHit()) {
                    return nextRes;
                }
            }

            return next.search(search, state);
        }
    }

    static class LazyOptional extends Node {
        final Node node;

        LazyOptional(Node node) {
            this.node = node;
        }

        @Override
        State search(DefaultSearch search, State state) {
            State res = next.search(search, state);
            if (res.isHit()) {
                return res;
            }

            res = node.search(search, state);
            if (res.isMiss()) {
                return res;
            }
            return next.search(search, res);
        }
    }

    static class PossessiveOptional extends Node {
        final Node node;

        PossessiveOptional(Node node) {
            this.node = node;
        }

        @Override
        State search(DefaultSearch search, State state) {
            State res = node.search(search, state);

            if (res.isHit()) {
                return next.search(search, res);
            } else {
                return next.search(search, state);
            }
        }
    }

    static abstract class RecursiveLoop extends Node {
        final Node body;
        final int countLocal;
        final int beginLocal;
        final int min, max;

        RecursiveLoop(Node body,
                      int countLocal,
                      int beginLocal,
                      int min,
                      int max) {
            this.body = body;
            this.countLocal = countLocal;
            this.beginLocal = beginLocal;
            this.min = min;
            this.max = max;
        }

        abstract State enterLoop(DefaultSearch search,
                                 State state);

        @Nullable
        @Override
        Class<?> firstType() {
            return body.firstType();
        }
    }

    static class GreedyLoop extends RecursiveLoop {

        GreedyLoop(Node body,
                   int countLocal,
                   int beginLocal,
                   int min,
                   int max) {
            super(body, countLocal, beginLocal, min, max);
        }

        @Override
        State enterLoop(DefaultSearch search, State state) {
            int save = search.locals[countLocal];

            State result;
            if (0 < min) {
                search.locals[countLocal] = 1;
                result = body.search(search, state);
            } else if (0 < max) {
                search.locals[countLocal] = 1;
                result = body.search(search, state);
                if (result.isMiss()) {
                    result = next.search(search, state);
                }
            } else {
                result = next.search(search, state);
            }

            search.locals[countLocal] = save;
            return result;
        }

        @Override
        State search(DefaultSearch search, State state) {
            if (state.end == search.locals[beginLocal]) {
                // our loop is not actually going anywhere but  theoretically it
                // would match an infinite number of times and then back off
                // to something under the max
                return next.search(search, state);
            }
            int count = search.locals[countLocal];

            if (count < min) {
                // increment and loop
                search.locals[countLocal] = count + 1;
                State result = body.search(search, state);
                if (result.isMiss()) {
                    // loop failed, de-increment
                    search.locals[countLocal] = count;
                }
                return result;
            }

            if (count < max) {
                search.locals[countLocal] = count + 1;
                State result = body.search(search, state);
                if (result.isMiss()) {
                    search.locals[countLocal] = count;
                } else {
                    // Since body loops back to next we don't need to call next
                    // before returning this result
                    return result;
                }
            }
            return next.search(search, state);
        }
    }

    static class LazyLoop extends RecursiveLoop {
        LazyLoop(Node body, int countLocal, int beginLocal, int min, int max) {
            super(body, countLocal, beginLocal, min, max);
        }

        @Override
        State enterLoop(DefaultSearch search, State state) {
            int save = search.locals[countLocal];
            State result;
            if (0 < min) {
                search.locals[countLocal] = 1;
                result = body.search(search, state);
            } else {
                result = next.search(search, state);
                if (result.isMiss() && 0 < max) {
                    search.locals[countLocal] = 1;
                    result = body.search(search, state);
                }
            }

            search.locals[countLocal] = save;
            return result;
        }

        @Override
        State search(DefaultSearch search, State state) {
            if (state.end == search.locals[beginLocal]) {
                // our loop is not actually going anywhere but theoretically it
                // would match the minimum number of times then find the next.
                return next.search(search, state);
            }

            int count = search.locals[countLocal];
            if (count < min) {
                search.locals[countLocal] = count + 1;
                State result = body.search(search, state);
                if (result.isMiss()) {
                    search.locals[countLocal] = count;
                }
                return result;
            }
            State result = next.search(search, state);
            if (result.isHit()) {
                return result;
            }
            if (count < max) {
                search.locals[countLocal] = count + 1;
                result = body.search(search, state);
                if (result.isMiss()) {
                    search.locals[countLocal] = count;
                }
                return result;
            }

            return State.miss();
        }
    }

    static class PossessiveLoop extends RecursiveLoop {
        PossessiveLoop(Node body,
                       int countLocal,
                       int beginLocal,
                       int min,
                       int max) {
            super(body, countLocal, beginLocal, min, max);
        }

        @Override
        State enterLoop(DefaultSearch search, State state) {
            int save = search.locals[countLocal];
            State result;
            if (0 < min) {
                search.locals[countLocal] = 1;
                result = body.search(search, state);
            } else if (0 < max) {
                search.locals[countLocal] = 1;
                result = body.search(search, state);
                if (result.isMiss()) {
                    result = next.search(search, state);
                }
            } else {
                search.locals[countLocal] = 1;
                result = body.search(search, state);
                if (result.isMiss()) {
                    result = next.search(search, state);
                }
            }

            search.locals[countLocal] = save;
            return result;
        }

        @Override
        State search(DefaultSearch search, State state) {
            if (state.end == search.locals[beginLocal]) {
                return next.search(search, state);
            }
            int count = search.locals[countLocal];

            if (count < min) {
                // increment and loop
                search.locals[countLocal] = count + 1;
                State result = body.search(search, state);
                if (result.isMiss()) {
                    // loop failed, de-increment
                    search.locals[countLocal] = count;
                }
                return result;
            } else if (count < max) {
                search.locals[countLocal] = count + 1;
                State result = body.search(search, state);
                if (result.isMiss()) {
                    search.locals[countLocal] = count;
                    return next.search(search, state);
                } else {
                    return result;
                }
            } else {
                search.locals[countLocal] = count + 1;
                State result = body.search(search, state);
                if (result.isHit()) {
                    // possessive over maximum
                    return State.miss();
                } else {
                    search.locals[countLocal] = count;
                    return next.search(search, state);
                }
            }
        }
    }

    static class LoopHead extends Node {
        private final RecursiveLoop recursiveLoop;
        private final int beginLocal;

        LoopHead(RecursiveLoop recursiveLoop, int beginLocal) {
            this.recursiveLoop = recursiveLoop;
            this.beginLocal = beginLocal;
        }

        @Override
        State search(DefaultSearch search, State state) {
            search.locals[beginLocal] = state.end;
            return recursiveLoop.enterLoop(search, state);
        }

        @Nullable
        @Override
        Class<?> firstType() {
            return recursiveLoop.firstType();
        }
    }

    static class InnerConditions extends Node {
        final int localAddr;
        private final boolean covered;
        Node[] conditions = new Node[2];
        int size = 0;

        InnerConditions(int localAddr, boolean covered) {
            this.localAddr = localAddr;
            this.covered = covered;
        }

        @Override
        public State search(DefaultSearch search, State state) {
            State pin = state.pin();
            for (int i = 0; i < size; i++) {
                State result = conditions[i].search(search, pin);

                if (result.isMiss() || (covered && !state.isCovered(result))) {
                    return State.miss();
                }
            }

            return next.search(search, state);
        }

        void addCondition(Node node) {
            if (conditions.length == size) {
                Node[] tmp = new Node[conditions.length * 2];
                System.arraycopy(conditions, 0, tmp, 0, size);
                conditions = tmp;
            }
            conditions[size++] = node;
        }
    }

    static class EnumMatch extends TypeMatch {
        final Enum<?> value;

        EnumMatch(Class labelType,
                  boolean seek,
                  boolean anonymous,
                  int group,
                  Enum<?> value) {
            super(labelType, seek, anonymous, group);
            this.value = value;
        }

        @Override
        boolean propertiesMatch(DefaultSearch search, Label label) {
            return label.getValue().equals(value);
        }
    }

    static class TypeMatch extends Node {
        final Class<?> labelType;
        final List<PropertyMatch> requiredProperties = new ArrayList<>();
        final boolean seek;
        final boolean anonymous;
        final int group;

        TypeMatch(Class labelType, boolean seek, boolean anonymous, int group) {
            this.labelType = labelType;
            this.seek = seek;
            this.anonymous = anonymous;
            this.group = group;
        }

        @Override
        public State search(DefaultSearch search, State state) {
            LabelIndex<?> labelIndex = search.document.getLabelIndex(labelType)
                    .insideSpan(state.getUncovered());
            if (!seek) {
                Optional<? extends Label<?>> labelOp = labelIndex.first();
                if (!labelOp.isPresent()) return State.miss();
                Label<?> label = labelOp.get();
                if (!propertiesMatch(search, label)) return State.miss();
                if (group != -1) {
                    search.groups[group] = label.getBegin();
                    search.groups[group + 1] = label.getEnd();
                    search.labels[group] = label;
                }
                State advance = state.advance(label.getBegin(), label.getEnd());
                State result = next.search(search, advance);
                if (result.isHit()) {
                    if (anonymous) {
                        return result;
                    }
                    return result.setBegin(label.getBegin());
                }

                return result;
            }
            for (Label<?> label : labelIndex) {
                if (!propertiesMatch(search, label)) continue;
                if (group != -1) {
                    search.groups[group] = label.getBegin();
                    search.groups[group + 1] = label.getEnd();
                    search.labels[group] = label;
                }
                State advance = state.advance(label.getBegin(), label.getEnd());
                State result = next.search(search, advance);
                if (result.isHit()) {
                    if (anonymous) {
                        return result;
                    }
                    return result.setBegin(label.getBegin());
                }
            }
            return State.miss();
        }

        @Nullable
        @Override
        Class<?> firstType() {
            return labelType;
        }

        boolean propertiesMatch(DefaultSearch search, Label label) {
            for (PropertyMatch requiredProperty : requiredProperties) {
                if (!requiredProperty.doesMatch(search, label)) return false;
            }
            return true;
        }

        void addPropertyMatch(String name, Object value) {
            requiredProperties.add(new ValuedPropertyMatch(name, value));
        }

        void addPropertyValueBackReference(String name,
                                           String group,
                                           Method backrefMethod) {
            requiredProperties.add(new PropertyValueBackReference(name, group,
                    backrefMethod));
        }

        void addNumberPropertyMatch(String name, Object value) {
            requiredProperties.add(new NumberPropertyMatch(name, value));
        }

        void addSpanBackReference(String name, String group) {
            requiredProperties.add(new SpanBackReference(name, group));
        }

        abstract class PropertyMatch {
            final String name;
            final Method readMethod;

            PropertyMatch(String name) {
                this.name = name;
                try {
                    readMethod = new PropertyDescriptor(name, labelType)
                            .getReadMethod();
                } catch (IntrospectionException e) {
                    throw new IllegalStateException(e);
                }
            }

            abstract boolean doesMatch(DefaultSearch search, Label<?> label);
        }

        class NumberPropertyMatch extends PropertyMatch {
            final Object value;

            NumberPropertyMatch(String name, Object value) {
                super(name);
                this.value = value;
            }

            @Override
            boolean doesMatch(DefaultSearch search, Label<?> label) {
                try {
                    Object invoke = readMethod.invoke(label.getValue());
                    double first = ((Number) invoke).doubleValue();
                    double second = ((Number) value).doubleValue();
                    return Math.abs(first - second) < 1e-10;
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("");
                }
            }
        }

        class ValuedPropertyMatch extends PropertyMatch {
            final Object value;

            ValuedPropertyMatch(String name, Object value) {
                super(name);
                this.value = value;
            }

            @Override
            boolean doesMatch(DefaultSearch search, Label<?> label) {
                try {
                    Object invoke = readMethod.invoke(label.getValue());
                    return value.equals(invoke);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("");
                }
            }
        }

        class PropertyValueBackReference extends PropertyMatch {
            private final String group;
            private final Method backrefMethod;

            PropertyValueBackReference(String name,
                                       String group,
                                       Method backrefMethod) {
                super(name);
                this.group = group;
                this.backrefMethod = backrefMethod;
            }

            @Override
            boolean doesMatch(DefaultSearch search, Label<?> label) {
                Label<?> groupLabel = search.getLabel(group)
                        .orElseThrow(
                                () -> new IllegalStateException("Not set"));
                try {
                    Object value = backrefMethod.invoke(groupLabel.getValue());
                    return value.equals(readMethod.invoke(label.getValue()));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("");
                }
            }
        }

        class SpanBackReference extends PropertyMatch {
            private final String group;

            SpanBackReference(String name, String group) {
                super(name);
                this.group = group;
            }

            @Override
            boolean doesMatch(DefaultSearch search, Label<?> label) {
                Span span = search.getSpan(group)
                        .orElseThrow(
                                () -> new IllegalStateException("Not set"));
                try {
                    return span.equals(readMethod.invoke(label.getValue()));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("");
                }
            }
        }
    }

    static class State {
        final int begin;
        final int end;
        final int limit;

        State(int begin, int end, int limit) {
            this.begin = begin;
            this.end = end;
            this.limit = limit;
        }

        static State miss() {
            return new State(-1, -1, -1);
        }

        State advance(int begin, int end) {
            return new State(begin, end, limit);
        }

        State setBegin(int begin) {
            return new State(begin, end, limit);
        }

        State pin() {
            return new State(begin, begin, end);
        }

        boolean isMiss() {
            return begin == -1;
        }

        boolean isHit() {
            return begin != -1;
        }

        boolean isCovered(State other) {
            return begin == other.begin && end == other.end;
        }

        Span getUncovered() {
            return new Span(end, limit);
        }

        Span getCovered() {
            return new Span(begin, end);
        }
    }

    /**
     *
     */
    class DefaultSearch implements Search {
        final Document document;
        final Label[] labels;
        final int[] groups;
        final int[] locals;
        boolean found;
        int from, to;
        State result;

        DefaultSearch(Document document, Span span) {
            this.document = document;
            labels = new Label[numberGroups];
            groups = new int[numberGroups * 2];
            locals = new int[numberLocals];
            from = span.getBegin();
            to = span.getEnd();
        }

        @Override
        public Optional<Label<?>> getLabel(String name) {
            Integer integer = groupNames.get(name);
            if (integer == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(labels[integer]);
        }

        @Override
        public Optional<Span> getSpan(String name) {
            Integer integer = groupNames.get(name);
            if (integer == null) {
                return Optional.empty();
            }
            return Optional
                    .of(new Span(groups[integer], groups[integer + 1]));
        }

        public boolean matches() {
            return found && from == result.begin && to == result.end;
        }

        @Override
        public boolean found() {
            return found;
        }

        @Override
        public boolean search() {
            Arrays.fill(groups, -1);
            Arrays.fill(labels, null);

            result = searchRoot.search(this, new State(from, from, to));
            from = result.end;
            return found = result.isHit();
        }

        @Override
        public boolean search(int begin, int end) {
            from = begin;
            to = end;
            return search();
        }

        @Override
        public boolean search(Span span) {
            return search(span.getBegin(), span.getEnd());
        }

        @Override
        public boolean match() {
            Arrays.fill(groups, -1);
            Arrays.fill(labels, null);

            result = root.search(this, new State(from, from, to));
            from = result.end;
            return found = result.isHit();
        }

        @Override
        public boolean match(int begin, int end) {
            from = begin;
            to = end;
            return match();
        }

        @Override
        public boolean match(Span span) {
            return match(span.getBegin(), span.getEnd());
        }


        @Override
        public Optional<Span> getSpan() {
            return Optional.of(result.getCovered()).filter(blah -> found);
        }

        @Override
        public Collection<String> getGroups() {
            return groupNames.keySet();
        }
    }

}
