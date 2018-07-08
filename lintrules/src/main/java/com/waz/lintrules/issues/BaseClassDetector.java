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
package com.waz.lintrules.issues;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import lombok.ast.ClassDeclaration;
import lombok.ast.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseClassDetector extends Detector implements Detector.JavaScanner {

    public static final Issue ISSUE_ACTIVITY = Issue.create(
        "com.waz.BaseActivity",
        "Every Activity should extend `com.waz.zclient.BaseActivity`",
        "To ensure that our dependency framework is working correctly, every " +
        "new activity needs to extend a BaseActivity.",
        Category.CORRECTNESS,
        7,
        Severity.WARNING,
        new Implementation(BaseClassDetector.class, Scope.JAVA_FILE_SCOPE));

    public static final Issue ISSUE_FRAGMENT = Issue.create(
        "com.waz.BaseFragment",
        "Every Fragment should extend `com.waz.zclient.pages.BaseFragment`",
        "To ensure that our dependency framework is working correctly, every " +
        "new fragment needs to extend a BaseFragment.",
        Category.CORRECTNESS,
        7,
        Severity.WARNING,
        new Implementation(BaseClassDetector.class, Scope.JAVA_FILE_SCOPE));

    public static final Issue ISSUE_DIALOG_FRAGMENT = Issue.create(
        "com.waz.BaseDialogFragment",
        "Every DialogFragment should extend `com.waz.zclient.pages.BaseDialogFragment`",
        "To ensure that our dependency framework is working correctly, every " +
        "new fragment needs to extend a BaseFragment.",
        Category.CORRECTNESS,
        7,
        Severity.WARNING,
        new Implementation(BaseClassDetector.class, Scope.JAVA_FILE_SCOPE));

    public static final Issue ISSUE_PREFERENCE_FRAGMENT = Issue.create(
        "com.waz.BasePreferenceFragment",
        "Every PreferenceFragment should extend `com.waz.zclient.pages.BasePreferenceFragment`",
        "To ensure that our dependency framework is working correctly, every " +
        "new fragment needs to extend a BaseFragment.",
        Category.CORRECTNESS,
        7,
        Severity.WARNING,
        new Implementation(BaseClassDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String[] BASE_CLASSES = new String[] {
        "com.waz.zclient.pages.BaseDialogFragment",
        "com.waz.zclient.pages.BaseFragment",
        "com.waz.zclient.pages.BasePreferenceFragment",
        "com.waz.zclient.BaseActivity"
    };

    private static final String CLASS_ACTIVITY = SdkConstants.CLASS_ACTIVITY;
    private static final String CLASS_FRAGMENT = SdkConstants.CLASS_FRAGMENT;
    private static final String CLASS_FRAGMENT_V4 = SdkConstants.CLASS_V4_FRAGMENT;
    private static final String CLASS_DIALOG_FRAGMENT = "android.app.DialogFragment";
    private static final String CLASS_DIALOG_FRAGMENT_V4 = "android.support.v4.app.DialogFragment";
    private static final String CLASS_PREFERENCE_FRAGMENT = "android.preference.PreferenceFragment";
    private static final String CONTAINER_CLASS_NAME = "com.waz.annotations.Container";

    private final List<String> reportedIssues = new ArrayList<>();

    public BaseClassDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(CLASS_ACTIVITY,
                             CLASS_FRAGMENT,
                             CLASS_FRAGMENT_V4,
                             CLASS_DIALOG_FRAGMENT,
                             CLASS_DIALOG_FRAGMENT_V4,
                             CLASS_PREFERENCE_FRAGMENT);
    }

    @Override
    public void checkClass(JavaContext context,
                           ClassDeclaration declaration,
                           Node node,
                           JavaParser.ResolvedClass resolvedClass) {
        if (declaration == null) {
            return;
        }

        if (isGeneratedBaseClass(resolvedClass)) {
            return;
        }

        if (isBaseClass(resolvedClass)) {
            return;
        }

        JavaParser.ResolvedClass superClass = resolvedClass.getSuperClass();
        while (superClass != null &&
               !reportedIssues.contains(resolvedClass.getSimpleName()) &&
               !isBaseClass(superClass) &&
               !isGeneratedBaseClass(superClass)) {
            if (superClass.getPackageName().startsWith("android")) {
                if (superClass.isSubclassOf(CLASS_PREFERENCE_FRAGMENT, false)) {
                    report(ISSUE_PREFERENCE_FRAGMENT, context, resolvedClass, node);
                    return;
                } else if (superClass.isSubclassOf(CLASS_DIALOG_FRAGMENT_V4, false)) {
                    report(ISSUE_DIALOG_FRAGMENT, context, resolvedClass, node);
                    return;
                } else if (superClass.isSubclassOf(CLASS_ACTIVITY, false)) {
                    report(ISSUE_ACTIVITY, context, resolvedClass, node);
                    return;
                } else if (superClass.isSubclassOf(CLASS_FRAGMENT_V4, false)) {
                    report(ISSUE_FRAGMENT, context, resolvedClass, node);
                    return;
                }
            }
            superClass = superClass.getSuperClass();
        }
    }

    private void report(Issue issue, JavaContext context, JavaParser.ResolvedClass resolvedClass, Node node) {
        context.report(issue, node, context.getLocation(node), "");
        reportedIssues.add(resolvedClass.getSimpleName());
    }

    private boolean isGeneratedBaseClass(JavaParser.ResolvedClass resolvedClass) {
        return resolvedClass != null && resolvedClass.getSimpleName().startsWith("BaseFragment<.Container>");
    }

    private boolean isBaseClass(JavaParser.ResolvedClass resolvedClass) {
        for (String baseClass : BASE_CLASSES) {
            if (resolvedClass.getName().startsWith(baseClass)) {
                return true;
            }
        }
        return false;
    }
}
