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
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class LayoutXmlValueDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
        "com.waz.LayoutXmlValue",
        "All values in the layout should be a separate XML value",
        "To make maintaining easier, all resource values should not be hardcoded",
        Category.CORRECTNESS,
        4,
        Severity.WARNING,
        new Implementation(LayoutXmlValueDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final static List<String> COLOR_ATTRIBUTES = Arrays.asList(SdkConstants.ATTR_COLOR,
                                                                       SdkConstants.ATTR_BACKGROUND,
                                                                       SdkConstants.ATTR_FOREGROUND,

                                                                       "textColorHint",
                                                                       "textColor");
    private final static List<String> DIMEN_ATTRIBUTES = Arrays.asList(SdkConstants.ATTR_PADDING,
                                                                       SdkConstants.ATTR_PADDING_BOTTOM,
                                                                       SdkConstants.ATTR_PADDING_LEFT,
                                                                       SdkConstants.ATTR_PADDING_RIGHT,
                                                                       SdkConstants.ATTR_PADDING_TOP,
                                                                       SdkConstants.ATTR_PADDING_START,
                                                                       SdkConstants.ATTR_PADDING_END,

                                                                       SdkConstants.ATTR_TEXT_SIZE,

                                                                       SdkConstants.ATTR_LAYOUT_WIDTH,
                                                                       SdkConstants.ATTR_LAYOUT_HEIGHT,

                                                                       SdkConstants.ATTR_LAYOUT_MARGIN,
                                                                       SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                                                                       SdkConstants.ATTR_LAYOUT_MARGIN_END,
                                                                       SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                                                                       SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                                                                       SdkConstants.ATTR_LAYOUT_MARGIN_START,
                                                                       SdkConstants.ATTR_LAYOUT_MARGIN_TOP,

                                                                       "alpha"
                                                                      );

    public LayoutXmlValueDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        final List<String> attributes = new ArrayList<>(COLOR_ATTRIBUTES);
        attributes.addAll(DIMEN_ATTRIBUTES);
        return attributes;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value.length() <= 0 || ((value.charAt(0) == '@' || value.charAt(0) == '?'))) {
            return;
        }

        // Make sure this is really one of the android: attributes
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String attributeName = attribute.getName();
        // clean out any prefix
        if (attributeName.contains(":")) {
            attributeName = attributeName.split(":")[1];
        }

        // match_parent, wrap_content and 0dp are fine in width and height
        if (attributeName.equals(SdkConstants.ATTR_LAYOUT_WIDTH) ||
            attributeName.equals(SdkConstants.ATTR_LAYOUT_HEIGHT)) {
            if (value.equals(SdkConstants.VALUE_MATCH_PARENT) ||
                value.equals(SdkConstants.VALUE_WRAP_CONTENT) ||
                value.equals(SdkConstants.VALUE_FILL_PARENT)) {
                return;
            }

            if (value.startsWith("0")) {
                return;
            }
        }

        if (COLOR_ATTRIBUTES.contains(attributeName)) {
            context.report(ISSUE,
                           attribute,
                           context.getLocation(attribute),
                           String.format("Hardcoded color value \"%1$s\", should use @color resource", value));
        } else if (DIMEN_ATTRIBUTES.contains(attributeName)) {
            context.report(ISSUE,
                           attribute,
                           context.getLocation(attribute),
                           String.format("Hardcoded dimen value \"%1$s\", should use @dimen resource", value));
        } else {
            context.report(ISSUE,
                           attribute,
                           context.getLocation(attribute),
                           String.format("Hardcoded value \"%1$s\", should use @ resource", value));
        }
    }
}
