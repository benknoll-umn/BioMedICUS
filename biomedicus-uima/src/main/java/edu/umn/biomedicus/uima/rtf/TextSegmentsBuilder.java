/*
 * Copyright (c) 2016 Regents of the University of Minnesota.
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

package edu.umn.biomedicus.uima.rtf;

import edu.umn.biomedicus.application.Biomedicus;
import edu.umn.biomedicus.common.types.text.Span;
import edu.umn.biomedicus.type.TextSegmentAnnotation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Builds text segments by taking all splits and checking if spans between them contain characters that are not
 * whitespace.
 *
 * @author Ben Knoll
 * @since 1.3.0
 */
class TextSegmentsBuilder {
    /**
     * The text segment splitting indices.
     */
    private final Collection<Integer> splits;

    /**
     * View to build in.
     */
    private final JCas jCas;

    /**
     * Initializes with an empty collection.
     *
     * @param jCas the view to build in.
     */
    TextSegmentsBuilder(JCas jCas) {
        this.splits = new ArrayList<>();
        splits.add(0);
        splits.add(jCas.getDocumentText().length());
        this.jCas = jCas;
    }

    /**
     * Adds an annotation the represents a structural component of a document.
     *
     * @param annotationType the annotation type.
     */
    void addAnnotations(int annotationType) {
        for (Annotation annotation : jCas.getAnnotationIndex(annotationType)) {
            splits.add(annotation.getBegin());
            splits.add(annotation.getEnd());
        }
    }

    /**
     * Builds text segments from the added annotations.
     */
    void buildInView() {
        String documentText = jCas.getDocumentText();
        int[] sortedSplits = splits.stream().mapToInt(i -> i).sorted().distinct().toArray();

        int prev = 0;
        for (int currentSplit : sortedSplits) {
            if (currentSplit != prev) {
                Span span = new Span(0, currentSplit);
                CharSequence segmentText = span.getCovered(documentText);
                if (Biomedicus.Patterns.NON_WHITESPACE.matcher(segmentText).find()) {
                    new TextSegmentAnnotation(jCas, prev, currentSplit).addToIndexes();
                }
                prev = currentSplit;
            }
        }
    }
}
