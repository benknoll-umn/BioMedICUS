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

package edu.umn.biomedicus.common.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility library for patterns for biomedicus.
 *
 * @since 1.1.0
 */
public final class Patterns {
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private Patterns() {
        throw new UnsupportedOperationException();
    }

    /**
     * A Pattern that will match against any character that is not whitespace.
     *
     * Using {@link java.util.regex.Matcher#find} will return whether or not a string has any non-whitespace characters.
     */
    public static final Pattern NON_WHITESPACE = Pattern.compile("\\S");

    /**
     * Loads a pattern from a file in the resource path by joining all of the lines of the file with an OR symbol '|'
     *
     * @param resourceName the path to the resource of regex statements to be joined
     * @return newly created pattern
     */
    public static Pattern loadPatternByJoiningLines(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream(resourceName)))){
            return Pattern.compile(reader.lines().collect(Collectors.joining("|")), Pattern.MULTILINE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}