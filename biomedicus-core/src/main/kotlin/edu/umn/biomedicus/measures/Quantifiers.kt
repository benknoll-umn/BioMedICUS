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

import com.google.inject.Inject
import com.google.inject.Singleton
import edu.umn.biomedicus.annotations.Setting
import edu.umn.biomedicus.common.SequenceDetector
import edu.umn.biomedicus.common.TextIdentifiers
import edu.umn.biomedicus.framework.DocumentProcessor
import edu.umn.biomedicus.framework.SearchExprFactory
import edu.umn.biomedicus.sentences.Sentence
import edu.umn.biomedicus.tokenization.ParseToken
import edu.umn.biomedicus.tokenization.Token
import edu.umn.nlpengine.Document
import edu.umn.nlpengine.TextRange

/**
 * A type of indefinite quantifier
 */
enum class IndefiniteQuantifierType {
    /**
     * Indefinite quantifier on the left, or lesser side of the number
     */
    LEFT,
    /**
     * Indefinite quantifier on the right, or greater side of the number
     */
    RIGHT,
    /**
     * Indefinite quantifier spanning around both sides of a number
     */
    LOCAL
}

/**
 * A part of a greater indefinite quantifier, the word or words that indicate that the quantifier is
 * indefinite
 *
 * @property startIndex the start of cue phrase
 * @property endIndex the end of the cue phrase
 * @property type
 */
data class IndefiniteQuantifierCue(
        override val startIndex: Int,
        override val endIndex: Int,
        val type: String
) : TextRange {
    constructor(
            textRange: TextRange,
            type: String
    ): this(textRange.startIndex, textRange.endIndex, type)

    constructor(
            startIndex: Int,
            endIndex: Int,
            indefiniteQuantifierType: IndefiniteQuantifierType
    ): this(startIndex, endIndex, indefiniteQuantifierType.toString())
}

/**
 * A standalone value which doesn't have a definite amount, like "a few" or "some."
 */
data class FuzzyValue(override val startIndex: Int, override val endIndex: Int) : TextRange

private val test: (String, Token) -> Boolean = { word, token: Token ->
    token.text.equals(word, true)
}

/**
 * A word or phrase which indicates a quantifier is indefinite
 */
@Singleton
class IndefiniteQuantifierCues @Inject constructor(
        @Setting("measures.indefiniteQuantifiers.leftPath") leftPath: String,
        @Setting("measures.indefiniteQuantifiers.rightPath") rightPath: String,
        @Setting("measures.indefiniteQuantifiers.localPath") localPath: String,
        @Setting("measures.indefiniteQuantifiers.fuzzyPath") fuzzyPath: String
) {
    val left = SequenceDetector.loadFromFile(leftPath, test)
    val right = SequenceDetector.loadFromFile(rightPath, test)
    val local = SequenceDetector.loadFromFile(localPath, test)
    val fuzzy = SequenceDetector.loadFromFile(fuzzyPath, test)
}


class IndefiniteQuantifierDetector @Inject internal constructor(
        private val cues: IndefiniteQuantifierCues
) : DocumentProcessor {
    override fun process(document: Document) {
        val view = TextIdentifiers.getSystemLabeledText(document)

        val sentences = view.labelIndex(Sentence::class)
        val tokens = view.labelIndex(ParseToken::class)

        val cueLabeler = view.labeler(IndefiniteQuantifierCue::class)
        val fuzzyLabeler = view.labeler(FuzzyValue::class)

        for (sentence in sentences) {
            val sentenceTokens = tokens.insideSpan(sentence).asList()

            cues.left.detectAll(sentenceTokens).forEach {
                cueLabeler.add(IndefiniteQuantifierCue(sentenceTokens[it.first].startIndex,
                        sentenceTokens[it.last].endIndex, IndefiniteQuantifierType.LEFT.name))
            }

            cues.right.detectAll(sentenceTokens).forEach {
                cueLabeler.add(IndefiniteQuantifierCue(sentenceTokens[it.first].startIndex,
                        sentenceTokens[it.last].endIndex, IndefiniteQuantifierType.RIGHT.name))
            }

            cues.local.detectAll(sentenceTokens).forEach {
                cueLabeler.add(IndefiniteQuantifierCue(sentenceTokens[it.first].startIndex,
                        sentenceTokens[it.last].endIndex, IndefiniteQuantifierType.LOCAL.name))
            }

            cues.fuzzy.detectAll(sentenceTokens).forEach {
                fuzzyLabeler.add(FuzzyValue(
                        sentenceTokens[it.first].startIndex,
                        sentenceTokens[it.last].endIndex
                ))
            }
        }
    }
}

data class StandaloneQuantifier(
        override val startIndex: Int,
        override val endIndex: Int
): TextRange

@Singleton
class StandaloneQuantifiers @Inject constructor(
        @Setting("measures.standaloneQuantifiersPath") path: String
) {
    val detector = SequenceDetector.loadFromFile(path, test)
}

class StandaloneQuantifierDetector @Inject constructor(
        standaloneQuantifiers: StandaloneQuantifiers
) : DocumentProcessor {
    val detector = standaloneQuantifiers.detector

    override fun process(document: Document) {
        val text = TextIdentifiers.getSystemLabeledText(document)

        val sentences = text.labelIndex(Sentence::class)
        val tokens = text.labelIndex(ParseToken::class)

        val labeler = text.labeler(StandaloneQuantifier::class)

        sentences
                .map { tokens.insideSpan(it).asList() }
                .forEach { sentenceTokens ->
                    detector.detectAll(sentenceTokens).forEach {
                        labeler.add(StandaloneQuantifier(sentenceTokens[it.first].startIndex,
                                sentenceTokens[it.last].endIndex))
                    }
                }
    }
}

data class Quantifier(
        override val startIndex: Int,
        override val endIndex: Int,
        val isExact: Boolean
) : TextRange {
    constructor(range: TextRange, isExact: Boolean): this(range.startIndex, range.endIndex, isExact)
}

@Singleton
class QuantifierExpression @Inject constructor(searchExprFactory: SearchExprFactory) {
    val expr = searchExprFactory.parse(
            "([?indef:IndefiniteQuantifierCue] ->)? (NumberRange | Number | fuzz:FuzzyValue | [PosTag<getPartOfSpeech=eDT> ParseToken<getText=\"a\">])"
    )
}

class QuantifierDetector @Inject constructor(
        expr: QuantifierExpression
) : DocumentProcessor {
    val expr = expr.expr

    override fun process(document: Document) {
        val systemView = TextIdentifiers.getSystemLabeledText(document)

        val searcher = expr.createSearcher(systemView)

        val labeler = systemView.labeler(Quantifier::class)

        while (searcher.search()) {
            val isExact = !searcher.getLabel("indef").isPresent
                    && !searcher.getLabel("fuzz").isPresent

            labeler.add(Quantifier(searcher.begin, searcher.end, isExact))
        }
    }
}
