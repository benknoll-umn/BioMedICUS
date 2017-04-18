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

package edu.umn.biomedicus.utilities;

import com.google.inject.Inject;
import edu.umn.biomedicus.annotations.ProcessorSetting;
import edu.umn.biomedicus.application.DocumentProcessor;
import edu.umn.biomedicus.common.labels.Labeler;
import edu.umn.biomedicus.common.labels.ValueLabeler;
import edu.umn.biomedicus.common.standard.StandardRecord;
import edu.umn.biomedicus.common.types.text.*;
import edu.umn.biomedicus.exc.BiomedicusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ParseAndSourceReader implements DocumentProcessor {
    private final Record record;
    private final String targetDocumentName;
    private final String charsetName;
    private final Path parsePath;
    private final Path sourcePath;

    private int index;
    private String text;
    private Labeler<ParseNode> parseNodeLabeler;

    @Inject
    public ParseAndSourceReader(Record record,
                                @ProcessorSetting("filePathKey") String filePathKey,
                                @ProcessorSetting("targetDocumentName") String targetDocumentName,
                                @ProcessorSetting("fileCharsetName") String charsetName) {
        this.record = record;
        this.targetDocumentName = targetDocumentName;
        this.charsetName = charsetName;

        parsePath = Paths.get(record.getMetadata(filePathKey));
        String sourceFileName = parsePath.getFileName().toString().replace(".parse", ".source");
        sourcePath = parsePath.resolveSibling(sourceFileName);
    }

    public ParseAndSourceReader(Record record,
                                String targetDocumentName,
                                String charsetName,
                                Path parsePath,
                                Path sourcePath) {
        this.record = record;
        this.targetDocumentName = targetDocumentName;
        this.charsetName = charsetName;
        this.parsePath = parsePath;
        this.sourcePath = sourcePath;
    }

    public static void main(String[] args) {
        Path parseFilePath = Paths.get(args[0]);
        Path sourceFilePath = Paths.get(args[1]);

        Record record = new StandardRecord(UUID.randomUUID().toString());

        ParseAndSourceReader parseAndSourceReader = new ParseAndSourceReader(record, "SystemView",
                "UTF-8", parseFilePath, sourceFilePath);
        try {
            parseAndSourceReader.process();
        } catch (BiomedicusException e) {
            e.printStackTrace();
        }
        System.out.println("Done");
    }

    @Override
    public void process() throws BiomedicusException {
        try {
            List<Span> sections = new ArrayList<>();
            StringBuilder documentBuilder = new StringBuilder();
            try (BufferedReader sourceReader = Files.newBufferedReader(sourcePath, Charset.forName(charsetName))) {
                int sectionStart = -1;

                String line;
                while (true) {
                    if (sectionStart == -1) {
                        line = sourceReader.readLine();
                        if (line == null) {
                            break;
                        }
                        if (line.startsWith("[")) {
                            sourceReader.readLine();
                            sectionStart = documentBuilder.length();
                        }
                    } else {
                        line = sourceReader.readLine();
                        if (line == null) {
                            throw new IllegalStateException("Document ended while in a section.");
                        } else if (line.startsWith("[")) {
                            Span section = new Span(sectionStart, documentBuilder.length());
                            sections.add(section);
                            sectionStart = -1;
                        } else {
                            documentBuilder.append(line).append("/n");
                        }
                    }
                }
            }

            text = documentBuilder.toString();
            Document document = record.createDocument(targetDocumentName, text);

            Labeler<Section> sectionLabeler = document.getLabeler(Section.class);
            Labeler<SectionContent> sectionContentLabeler = document.getLabeler(SectionContent.class);
            for (Span section : sections) {
                sectionLabeler.value(new Section(null)).label(section);
                sectionContentLabeler.value(new SectionContent()).label(section);
            }

            index = 0;

            parseNodeLabeler = document.getLabeler(ParseNode.class);

            ValueLabeler sentenceLabeler = document.getLabeler(Sentence.class).value(new Sentence());

            PtbReader ptbReader = PtbReader.createFromFile(parsePath);
            List<PtbReader.Node> sentences = ptbReader.parse();
            for (PtbReader.Node sentence : sentences) {
                ParseNodeLabelSeed parseNodeLabelSeed = recursiveBuildTree(sentence);
                parseNodeLabeler.value(new ParseNode(null, parseNodeLabelSeed.label))
                        .label(parseNodeLabelSeed.span);
                sentenceLabeler.label(parseNodeLabelSeed.span);
            }
        } catch (IOException e) {
            throw new BiomedicusException(e);
        }
    }

    private ParseNodeLabelSeed recursiveBuildTree(PtbReader.Node node) throws BiomedicusException {
        ParseNodeLabelSeed parseNodeLabelSeed = new ParseNodeLabelSeed();
        if (node.getChildren().size() == 0) {
            parseNodeLabelSeed.label = node.getLabel();
            int begin;
            if ("-NONE-".equals(parseNodeLabelSeed.label)) {
                begin = index;
            } else {
                String word = node.getWord();
                begin = text.indexOf(word, index);
                index = begin + word.length();
            }
            parseNodeLabelSeed.span = new Span(begin, index);
        } else {
            List<PtbReader.Node> children = node.getChildren();
            List<ParseNodeLabelSeed> seedChildren = new ArrayList<>();
            for (PtbReader.Node childNode : children) {
                ParseNodeLabelSeed childSeed = recursiveBuildTree(childNode);
                seedChildren.add(childSeed);
            }
            int begin = seedChildren.get(0).span.getBegin();
            int end = seedChildren.get(seedChildren.size() - 1).span.getEnd();
            parseNodeLabelSeed.span = new Span(begin, end);
            for (ParseNodeLabelSeed seedChild : seedChildren) {
                parseNodeLabeler.value(new ParseNode(parseNodeLabelSeed.span, seedChild.label)).label(seedChild.span);
            }
        }
        return parseNodeLabelSeed;
    }

    private static class ParseNodeLabelSeed {
        private Span span;
        private String label;
    }
}
