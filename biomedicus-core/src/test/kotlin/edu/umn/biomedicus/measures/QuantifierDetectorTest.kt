/*
 * Copyright (c) 2018 Regents of the University of Minnesota.
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

package edu.umn.biomedicus.measures

import edu.umn.biomedicus.common.TextIdentifiers
import edu.umn.biomedicus.framework.LabelAliases
import edu.umn.biomedicus.framework.SearchExprFactory
import edu.umn.biomedicus.numbers.NumberType
import edu.umn.biomedicus.sentences.Sentence
import edu.umn.biomedicus.tagging.PosTag
import edu.umn.biomedicus.tokenization.ParseToken
import edu.umn.nlpengine.Document
import edu.umn.nlpengine.StandardDocument
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import java.math.BigInteger

class QuantifierDetectorTest {

    val document: Document = StandardDocument("doc")

    val labelAliases = LabelAliases()

    val searchExprFactory = SearchExprFactory(labelAliases)

    val quantifierDetector: QuantifierDetector

    init {
        labelAliases.addAlias("Number", Number::class.java)
        labelAliases.addAlias("IndefiniteQuantifierCue", IndefiniteQuantifierCue::class.java)
        labelAliases.addAlias("Sentence", Sentence::class.java)
        labelAliases.addAlias("FuzzyValue", FuzzyValue::class.java)
        labelAliases.addAlias("Quantifier", Quantifier::class.java)
        labelAliases.addAlias("NumberRange", NumberRange::class.java)
        labelAliases.addAlias("ParseToken", ParseToken::class.java)
        labelAliases.addAlias("PosTag", PosTag::class.java)

        quantifierDetector = QuantifierDetector(QuantifierExpression(searchExprFactory))
    }

    @Test
    fun testIndefiniteQuantifierNumber() {
        val view = document.attachText(TextIdentifiers.SYSTEM, "Beer: Around 25 bottles a week.")

        view.labeler(Number::class)
                .add(Number(13, 15, BigInteger.valueOf(25).toString(),
                        BigInteger.ONE.toString(), NumberType.CARDINAL))

        view.labeler(IndefiniteQuantifierCue::class)
                .add(IndefiniteQuantifierCue(6, 12,
                        IndefiniteQuantifierType.LOCAL))

        view.labeler(Sentence::class).add(Sentence(0, 24))

        quantifierDetector.process(document)

        val quantifiers = view.labelIndex(Quantifier::class.java)

        assertTrue(quantifiers.atLocation(6, 15).size == 1)
    }
}