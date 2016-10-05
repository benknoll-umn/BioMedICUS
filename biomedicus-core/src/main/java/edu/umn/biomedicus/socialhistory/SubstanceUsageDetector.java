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

package edu.umn.biomedicus.socialhistory;

import com.google.inject.Inject;
import edu.umn.biomedicus.application.DocumentProcessor;
import edu.umn.biomedicus.common.labels.Label;
import edu.umn.biomedicus.common.labels.Labels;
import edu.umn.biomedicus.common.types.semantics.SocialHistoryCandidate;
import edu.umn.biomedicus.common.types.semantics.SubstanceUsageKind;
import edu.umn.biomedicus.common.types.text.DependencyParse;
import edu.umn.biomedicus.common.types.text.Document;
import edu.umn.biomedicus.common.types.text.Sentence;
import edu.umn.biomedicus.exc.BiomedicusException;

import java.util.HashMap;
import java.util.Map;

public class SubstanceUsageDetector implements DocumentProcessor {

    private final Map<SubstanceUsageKind, KindSubstanceUsageDetector> kindMap = createKindMap();

    private Map<SubstanceUsageKind, KindSubstanceUsageDetector> createKindMap() {
        Map<SubstanceUsageKind, KindSubstanceUsageDetector> kindSubstanceUsageDetectorMap = new HashMap<>();

        kindSubstanceUsageDetectorMap.put(SubstanceUsageKind.NICOTINE, new TobaccoKindSubstanceUsageDetector());

        return kindSubstanceUsageDetectorMap;
    }

    private final Document document;

    @Inject
    SubstanceUsageDetector(Document document) {
        this.document = document;
    }

    @Override
    public void process() throws BiomedicusException {
        for (Label<SocialHistoryCandidate> socialHistoryCandidateLabel : document.labels(SocialHistoryCandidate.class)) {
            SubstanceUsageKind substanceUsageKind = socialHistoryCandidateLabel.value().getSubstanceUsageKind();
            kindMap.get(substanceUsageKind).processCandidate(document, socialHistoryCandidateLabel);
        }
    }
}
