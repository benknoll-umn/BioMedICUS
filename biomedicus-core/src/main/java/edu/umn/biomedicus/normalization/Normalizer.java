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

package edu.umn.biomedicus.normalization;

import edu.umn.biomedicus.common.StandardViews;
import edu.umn.biomedicus.common.types.syntax.PartOfSpeech;
import edu.umn.biomedicus.common.types.text.ImmutableNormForm;
import edu.umn.biomedicus.common.types.text.NormForm;
import edu.umn.biomedicus.common.types.text.ParseToken;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DocumentProcessor;
import edu.umn.biomedicus.framework.store.Document;
import edu.umn.biomedicus.framework.store.Label;
import edu.umn.biomedicus.framework.store.LabelIndex;
import edu.umn.biomedicus.framework.store.Labeler;
import edu.umn.biomedicus.framework.store.TextView;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Normalizer implements DocumentProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(Normalizer.class);

  private final NormalizerModel normalizerModel;

  @Inject
  Normalizer(NormalizerModel normalizerModel) {
    this.normalizerModel = normalizerModel;
  }

  @Override
  public void process(Document document) throws BiomedicusException {
    LOGGER.debug("Normalizing tokens in a document.");
    TextView textView = StandardViews.getSystemView(document);

    LabelIndex<ParseToken> parseTokenLabelIndex = textView.getLabelIndex(ParseToken.class);
    LabelIndex<PartOfSpeech> partOfSpeechLabelIndex = textView.getLabelIndex(PartOfSpeech.class);
    Labeler<NormForm> normFormLabeler = textView.getLabeler(NormForm.class);

    for (Label<ParseToken> parseTokenLabel : parseTokenLabelIndex) {
      PartOfSpeech partOfSpeech = partOfSpeechLabelIndex
          .withTextLocation(parseTokenLabel)
          .orElseThrow(() -> new BiomedicusException(
              "Part of speech label not found for parse token label"))
          .value();
      String normalForm = normalizerModel.normalize(parseTokenLabel.value(), partOfSpeech);

      normFormLabeler.value(ImmutableNormForm.builder()
          .normalForm(normalForm)
          .build())
          .label(parseTokenLabel);
    }
  }
}
