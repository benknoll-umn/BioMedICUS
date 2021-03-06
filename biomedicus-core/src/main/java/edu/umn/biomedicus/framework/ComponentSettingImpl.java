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

package edu.umn.biomedicus.framework;

import edu.umn.biomedicus.annotations.ComponentSetting;
import java.io.Serializable;
import java.lang.annotation.Annotation;

/**
 * An implementation of the annotation {@link ComponentSetting} so that it can be instantiated and
 * bound to an instance to be used as an injection key.
 */
public final class ComponentSettingImpl implements ComponentSetting, Serializable {

  private final String value;

  ComponentSettingImpl(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Setting key cannot be null");
    }

    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }

  @Override
  public int hashCode() {
    return (127 * "value".hashCode()) ^ value.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ComponentSetting)) {
      return false;
    }
    ComponentSetting other = (ComponentSetting) obj;
    return value.equals(other.value());
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return ComponentSetting.class;
  }

  @Override
  public String toString() {
    return "ComponentSetting(" + value + ")";
  }
}
