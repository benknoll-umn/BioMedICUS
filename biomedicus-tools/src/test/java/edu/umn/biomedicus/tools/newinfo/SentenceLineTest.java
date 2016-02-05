/*
 * Copyright (c) 2015 Regents of the University of Minnesota.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umn.biomedicus.tools.newinfo;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit test for {@link SentenceLine}.
 */
public class SentenceLineTest {
    @Test
    public void testLine() throws Exception {
        SentenceLine sentenceLine = new SentenceLine(4, "This is \na sentence.\t");

        Assert.assertEquals("4\tThis is \\na sentence.\\t\n", sentenceLine.line());
    }
}