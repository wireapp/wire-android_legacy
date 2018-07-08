/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.checkstyle;

import java.util.regex.Pattern;

class NamingConventionsDetector {

    private static final Pattern PREFIX_M_NAME_PATTERN = Pattern.compile("^m[A-Z0-9].*");
    private static final Pattern PREFIX_STATIC_NAME_PATTERN = Pattern.compile("^s[A-Z0-9].*");

    private static final Pattern WRONG_CONSTANT_NAME_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("[A-Z].*");
    private static final Pattern WRONG_NON_MEMBER_NAME_PATTERN = Pattern.compile("^[msp][A-Z0-9].*");

    private NamingConventionsDetector() {}

    public static boolean detectsMemberPrefix(String variableName) {
        return PREFIX_M_NAME_PATTERN.matcher(variableName).matches();
    }

    public static boolean detectsWrongConstantNaming(String variableName) {
        return WRONG_CONSTANT_NAME_PATTERN.matcher(variableName).matches();
    }

    public static boolean detectsUnderscore(String variableName) {
        return variableName.contains("_");
    }

    public static boolean detectsUpperCaseBeginning(String variableName) {
        return CAMEL_CASE_PATTERN.matcher(variableName).matches();
    }

    public static boolean detectsPrefix(String variableName) {
        return WRONG_NON_MEMBER_NAME_PATTERN.matcher(variableName).matches();
    }

    public static boolean detectsStaticPrefix(String variableName) {
        return PREFIX_STATIC_NAME_PATTERN.matcher(variableName).matches();
    }
}
