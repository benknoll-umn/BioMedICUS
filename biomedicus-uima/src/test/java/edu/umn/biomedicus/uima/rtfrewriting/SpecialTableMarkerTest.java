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

package edu.umn.biomedicus.uima.rtfrewriting;

import edu.umn.biomedicus.uima.rtfrewriting.SpecialTableMarker;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Unit test for {@link SpecialTableMarker}.
 */
public class SpecialTableMarkerTest {

    @Test
    public void testInsertInTable() throws Exception {
        String document = "{\\atbl;1;2;3;}";

        SpecialTableMarker specialTableMarker = new SpecialTableMarker(document, "\\u2222221B", "\\atbl");

        assertEquals(specialTableMarker.insertInTable(), "{\\atbl;1;2;3;\\u2222221B 4}");
    }
}