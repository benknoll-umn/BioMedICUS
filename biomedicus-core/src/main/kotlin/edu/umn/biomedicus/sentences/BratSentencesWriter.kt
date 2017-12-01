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

package edu.umn.biomedicus.sentences

import edu.umn.biomedicus.annotations.ProcessorSetting
import edu.umn.biomedicus.common.StandardViews
import edu.umn.biomedicus.framework.DocumentProcessor
import edu.umn.biomedicus.framework.store.Document
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

val newlineTab = Regex(" *\\n+ *")

class BratSentencesWriter @Inject internal constructor(
        @ProcessorSetting("outputDirectory") private val outputDirectory: Path
) : DocumentProcessor {
    override fun process(document: Document) {
        val documentId = java.lang.String.format("%07d", document.documentId.toInt())
        val systemView = StandardViews.getSystemView(document)

        val text = systemView.text
        val textPath = outputDirectory.resolve(documentId + ".txt")
        val annPath = outputDirectory.resolve(documentId + ".ann")

        Files.createDirectories(outputDirectory)
        textPath.toFile().writeText(text, StandardCharsets.UTF_8)

        annPath.toFile().bufferedWriter(charset = StandardCharsets.UTF_8).use { writer ->
            val sentences = systemView.getLabelIndex(Sentence::class.java)

            var i = 1
            for (sentence in sentences) {
                val covered = sentence.coveredText(systemView.text)
                val builder = StringBuilder(covered)
                writer.write("T${i++}\tSentence ${sentence.startIndex}")
                var offset = 0
                newlineTab.findAll(covered).forEach {
                    writer.write(" ${sentence.startIndex + it.range.start};${sentence.startIndex + it.range.last + 1}")
                    builder.replace(it.range.start - offset, it.range.endInclusive + 1 - offset, " ")
                    offset += it.range.endInclusive - it.range.start
                }
                writer.write(" ${sentence.endIndex}\t$builder")
                writer.newLine()
            }
        }
    }

}