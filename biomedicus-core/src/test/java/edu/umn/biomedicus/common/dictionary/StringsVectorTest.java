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

package edu.umn.biomedicus.common.dictionary;

import static org.testng.Assert.*;

import edu.umn.biomedicus.common.dictionary.StringsVector.Builder;
import org.testng.annotations.Test;

public class StringsVectorTest {

  @Test
  public void testBytes() throws Exception {
    Builder builder = new Builder();
    builder.addTerm(new StringIdentifier(5));
    builder.addTerm(new StringIdentifier(5));
    builder.addTerm(new StringIdentifier(6));
    builder.addTerm(new StringIdentifier(10));

    StringsVector vect = builder.build();
    byte[] bytes = vect.getBytes();

    StringsVector newVector = new StringsVector(bytes);

    assertEquals(newVector.size(), 4);
    assertEquals(newVector.get(0), new StringIdentifier(5));
    assertEquals(newVector.get(1), new StringIdentifier(5));
    assertEquals(newVector.get(2), new StringIdentifier(6));
    assertEquals(newVector.get(3), new StringIdentifier(10));
  }
}