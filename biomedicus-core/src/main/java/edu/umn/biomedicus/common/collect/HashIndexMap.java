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

package edu.umn.biomedicus.common.collect;

import java.util.Collection;
import java.util.HashMap;

/**
 *
 */
public class HashIndexMap<T> implements IndexMap<T> {

  private final HashMap<T, Integer> indexMap;

  private final HashMap<Integer, T> instanceMap;

  public HashIndexMap() {
    indexMap = new HashMap<>();
    instanceMap = new HashMap<>();
  }

  public HashIndexMap(Collection<? extends T> collection) {
    indexMap = new HashMap<>();
    instanceMap = new HashMap<>();
    collection.forEach(this::addItem);
  }

  @Override
  public int size() {
    return indexMap.size();
  }

  @Override
  public boolean contains(T item) {
    return indexMap.containsKey(item);
  }

  @Override
  public void addItem(T item) {
    if (!(indexMap.containsKey(item))) {
      int index = indexMap.size();
      indexMap.put(item, index);
      instanceMap.put(index, item);
    }
  }

  @Override
  public T forIndex(int index) {
    return instanceMap.get(index);
  }

  @Override
  public Integer indexOf(T item) {
    return indexMap.get(item);
  }
}
