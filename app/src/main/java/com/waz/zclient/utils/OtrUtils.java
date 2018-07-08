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
package com.waz.zclient.utils;

public final class OtrUtils {

    private static final String BOLD_PREFIX = "[[";
    private static final String BOLD_SUFFIX = "]]";
    private static final String SEPARATOR = " ";

    private OtrUtils() {

    }

    public static String getFormattedFingerprint(String fingerprint) {
        int currentChunkSize = 0;
        boolean bold = true;

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < fingerprint.length(); i++) {
            if (currentChunkSize == 0 && bold) {
                sb.append(BOLD_PREFIX);
            }
            sb.append(fingerprint.charAt(i));
            currentChunkSize++;

            if (currentChunkSize == 2 || i == fingerprint.length() - 1) {
                if (bold) {
                    sb.append(BOLD_SUFFIX);
                }
                bold = !bold;
                if (i < fingerprint.length() - 1) {
                    sb.append(SEPARATOR);
                }
                currentChunkSize = 0;
            }
        }
        return sb.toString();
    }

}
