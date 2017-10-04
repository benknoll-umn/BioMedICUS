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

package edu.umn.biomedicus.tokenization;

import edu.umn.biomedicus.framework.store.Span;
import edu.umn.biomedicus.framework.store.TextLocation;
import edu.umn.biomedicus.measures.UnitRecognizer;
import java.io.IOException;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PennLikePhraseTokenizer {

  /**
   * Any sequence of 1 or more character that are not unicode whitespace.
   */
  private static final Pattern WORDS = Pattern.compile("[^\\p{Z}\\p{C}]+");

  private static final Pattern TRAILING_PERIOD = Pattern.compile("\\.$");

  /**
   * Break on any symbol punctuation unless it is a . or a comma with a number or symbol on either
   * side.
   */
  private static final Pattern MID_BREAKS = Pattern.compile(
      "[\\p{S}\\p{P}&&[^.,'’]]|^'|^,|^’|(?<=[^\\p{N}]),(?=[^\\p{N}])|(?<=[^\\p{N}]),(?=[\\p{N}])|(?<=[\\p{N}]),(?=[^\\p{N}])"
  );

  /**
   * Break possessives and contractions ', 's, n't, 'll, 've, 're in both uppercase and lowercase
   * forms. Break the unicode Pe (close brackets) and Pf (final quotation). Break all unicode
   * punctuation (P) except period. Break the unicode currency symbols Sc.
   */
  private static final Pattern END_BREAKS = Pattern.compile(
      "(?<=(('[SsDdMm])|(n't)|(N'T)|('ll)|('LL)|('ve)|('VE)|('re)|('RE)|[\\p{S}\\p{P}&&[^.]]))$"
  );



  private static final Pattern NUMBER_WORD = Pattern.compile(".*?[0-9]++(?<suffix>[\\p{Alpha}]++)$");

  private static final UnitRecognizer RECOGNIZER;

  static {
    try {
      RECOGNIZER = UnitRecognizer.createFactory().create();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private final CharSequence sentenceText;


  PennLikePhraseTokenizer(CharSequence sentenceText) {
    this.sentenceText = sentenceText;

  }

  public static Stream<Span> tokenizeSentence(CharSequence sentenceText) {
    PennLikePhraseTokenizer sentenceTokenizer = new PennLikePhraseTokenizer(sentenceText);

    return sentenceTokenizer.startStreamWithWords()
        .flatMap(sentenceTokenizer::splitTrailingPeriod)
        .flatMap(sentenceTokenizer::splitWordByMiddleBreaks)
        .flatMap(sentenceTokenizer::splitWordByEndBreaks)
        .flatMap(sentenceTokenizer::splitUnitsOffTheEnd)
        .map(TokenCandidate::toSpan);
  }

  public static Stream<Span> tokenizePhrase(CharSequence phraseText) {
    PennLikePhraseTokenizer tokenizer = new PennLikePhraseTokenizer(phraseText);

    return tokenizer.startStreamWithWords()
        .flatMap(tokenizer::splitWordByMiddleBreaks)
        .flatMap(tokenizer::splitWordByEndBreaks)
        .flatMap(tokenizer::splitUnitsOffTheEnd)
        .map(TokenCandidate::toSpan);
  }

  Stream<TokenCandidate> startStreamWithWords() {
    Stream.Builder<TokenCandidate> builder = Stream.builder();

    Matcher characters = WORDS.matcher(sentenceText);
    int last = -1;
    while (characters.find()) {
      last = characters.end();
    }

    if (last == -1) {
      return Stream.empty();
    }

    Matcher words = WORDS.matcher(sentenceText);
    while (words.find()) {
      int start = words.start();
      int end = words.end();
      boolean isLast = end == last;
      TokenCandidate tokenCandidate = new TokenCandidate(start, end, isLast);
      builder.add(tokenCandidate);
    }
    return builder.build();
  }

  Stream<TokenCandidate> splitTrailingPeriod(TokenCandidate tokenCandidate) {
    if (!tokenCandidate.isLast) {
      return Stream.of(tokenCandidate);
    } else {
      Matcher matcher = TRAILING_PERIOD.matcher(tokenCandidate.getCovered(sentenceText));
      if (matcher.find()) {
        return Stream
            .of(new TokenCandidate(tokenCandidate.derelativize(new Span(0, matcher.start())),
                    false),
                new TokenCandidate(
                    tokenCandidate.derelativize(new Span(matcher.start(), matcher.end())), true));
      } else {
        return Stream.of(tokenCandidate);
      }
    }
  }

  Stream<TokenCandidate> splitWordByMiddleBreaks(TokenCandidate tokenCandidate) {
    CharSequence tokenText = tokenCandidate.getCovered(sentenceText);

    Stream.Builder<TokenCandidate> builder = Stream.builder();

    Matcher midBreaksMatcher = MID_BREAKS.matcher(tokenText);
    if (midBreaksMatcher.find()) {
      int begin = midBreaksMatcher.start();
      int end = midBreaksMatcher.end();

      Span beginSplit = tokenCandidate.derelativize(new Span(0, begin));
      if (beginSplit.length() > 0) {
        builder.add(new TokenCandidate(beginSplit, false));
      }

      Span matchedSplit = tokenCandidate.derelativize(new Span(begin, end));
      builder.add(new TokenCandidate(matchedSplit, false));

      while (midBreaksMatcher.find()) {
        begin = midBreaksMatcher.start();
        Span beforeSplit = tokenCandidate.derelativize(new Span(end, begin));
        if (beforeSplit.length() > 0) {
          builder.add(new TokenCandidate(beforeSplit, false));
        }

        end = midBreaksMatcher.end();
        matchedSplit = tokenCandidate.derelativize(new Span(begin, end));
        if (matchedSplit.length() > 0) {
          builder.add(new TokenCandidate(matchedSplit, false));
        }
      }
      Span lastSplit = tokenCandidate.derelativize(new Span(end, tokenText.length()));
      if (lastSplit.length() > 0) {
        builder.add(new TokenCandidate(lastSplit, tokenCandidate.isLast));
      }
    } else {
      builder.add(tokenCandidate);
    }
    return builder.build();
  }

  Stream<TokenCandidate> splitWordByEndBreaks(TokenCandidate tokenCandidate) {
    LinkedList<TokenCandidate> candidates = new LinkedList<>();

    while (true) {
      CharSequence tokenText = tokenCandidate.getCovered(sentenceText);
      Matcher endBreaksMatcher = END_BREAKS.matcher(tokenText);
      if (endBreaksMatcher.find()) {
        int start = endBreaksMatcher.start(1);
        Span rest = tokenCandidate.derelativize(new Span(0, start));
        Span endSplit = tokenCandidate.derelativize(new Span(start, endBreaksMatcher.end()));
        candidates.addFirst(new TokenCandidate(endSplit, tokenCandidate.isLast));
        tokenCandidate = new TokenCandidate(rest, false);
      } else {
        if (tokenCandidate.getBegin() != tokenCandidate.getEnd()) {
          candidates.addFirst(tokenCandidate);
        }
        return candidates.stream();
      }
    }
  }

  Stream<TokenCandidate> splitUnitsOffTheEnd(TokenCandidate tokenCandidate) {
    CharSequence tokenText = tokenCandidate.getCovered(sentenceText);
    Matcher matcher = NUMBER_WORD.matcher(tokenText);
    if (matcher.matches()) {
      String suffix = matcher.group("suffix");
      if (suffix != null && RECOGNIZER.isUnitOfMeasureWord(suffix)) {
        int numBegin = tokenCandidate.getBegin();
        int numEnd = tokenCandidate.getEnd() - suffix.length();
        return Stream.of(new TokenCandidate(numBegin, numEnd, false),
            new TokenCandidate(numEnd, tokenCandidate.end(), tokenCandidate.isLast()));
      }
    }
    return Stream.of(tokenCandidate);
  }

  class TokenCandidate implements TextLocation {

    private final int begin;
    private final int end;
    private final boolean isLast;

    TokenCandidate(Span span, boolean isLast) {
      begin = span.getBegin();
      end = span.getEnd();
      this.isLast = isLast;
    }

    TokenCandidate(int begin, int end, boolean isLast) {
      this.begin = begin;
      this.end = end;
      this.isLast = isLast;
    }

    @Override
    public int getBegin() {
      return begin;
    }

    @Override
    public int getEnd() {
      return end;
    }

    public boolean isLast() {
      return isLast;
    }
  }
}
