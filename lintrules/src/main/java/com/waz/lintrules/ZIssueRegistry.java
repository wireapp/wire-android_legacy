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
package com.waz.lintrules;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.waz.lintrules.issues.BaseClassDetector;
import com.waz.lintrules.issues.LayoutXmlValueDetector;
import com.waz.lintrules.issues.MathUtilsDetector;
import com.waz.lintrules.issues.ObjectAnimatorPropertyDetector;
import com.waz.lintrules.issues.WrongTimberUsageDetector;

import java.util.Arrays;
import java.util.List;

public class ZIssueRegistry extends IssueRegistry {

    @Override
    public List<Issue> getIssues() {
        return Arrays.asList(BaseClassDetector.ISSUE_DIALOG_FRAGMENT,
                             BaseClassDetector.ISSUE_FRAGMENT,
                             BaseClassDetector.ISSUE_PREFERENCE_FRAGMENT,
                             LayoutXmlValueDetector.ISSUE,
                             WrongTimberUsageDetector.ISSUE_D_USAGE,
                             WrongTimberUsageDetector.ISSUE_LOG,
                             WrongTimberUsageDetector.ISSUE_FORMAT,
                             WrongTimberUsageDetector.ISSUE_THROWABLE,
                             WrongTimberUsageDetector.ISSUE_BINARY,
                             WrongTimberUsageDetector.ISSUE_ARG_TYPES,
                             MathUtilsDetector.ISSUE_FLOAT_EQUALS,
                             ObjectAnimatorPropertyDetector.ISSUE
                            );
    }
}
