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

package edu.umn.nlpengine

import java.io.Closeable

interface Runner {
    fun processArtifact(artifact: Artifact)

    fun done() { }
}

interface ArtifactProcessor {
    fun process(artifact: Artifact)
}

interface DocumentProcessor  {
    fun process(document: Document)
}

interface Aggregator {
    fun process(artifact: Artifact)

    fun done()
}

interface ArtifactSource : Closeable {
    fun estimateTotal(): Long

    fun tryAdvance(consumer: (Artifact) -> Unit): Boolean
}