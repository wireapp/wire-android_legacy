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

import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import lombok.ast.AstVisitor;
import lombok.ast.Expression;
import lombok.ast.MethodInvocation;
import lombok.ast.StrictListAccessor;
import lombok.ast.StringLiteral;
import lombok.ast.VariableReference;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ObjectAnimatorPropertyDetector extends Detector implements Detector.JavaScanner {

    public static final Issue ISSUE = Issue.create(
        "com.waz.ObjectAnimatorProperty",
        "Use or create a Property",
        "When using ObjectAnimators it is recommended and more performant to use the associated property " +
        "like {@link View#ALPHA} or {@link View#TRANSLATE_X}.",
        Category.PERFORMANCE,
        6,
        Severity.WARNING,
        new Implementation(ObjectAnimatorPropertyDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("ofFloat", "ofInt");
    }

    @Override
    public void visitMethod(JavaContext context, AstVisitor visitor, MethodInvocation node) {
        if (!(node.astOperand() instanceof VariableReference)) {
            return;
        }
        final VariableReference ref = (VariableReference) node.astOperand();
        if (!"ObjectAnimator".equals(ref.astIdentifier().astValue())) {
            return;
        }

        final StrictListAccessor<Expression, MethodInvocation> astArguments = node.astArguments();
        if (astArguments.size() <= 2) {
            return;
        }
        final Iterator<Expression> iterator = astArguments.iterator();
        iterator.next(); // ignored to get the second
        final Expression property = iterator.next();
        if (property instanceof StringLiteral) {
            context.report(ISSUE,
                           context.getLocation(node),
                           String.format("String '%s' should be replaced", ((StringLiteral) property).astValue()));
            return;
        }

        if (context.resolve(property) == null) {
            return;
        }

        if (property instanceof VariableReference) {
            if (!isSubclassOf(context, (VariableReference) property, "android.util.Property") &&
                !isSubclassOf(context, (VariableReference) property, "com.nineoldandroids.util.Property")) {
                context.report(ISSUE, context.getLocation(node), String.format("'%s' should be replaced with a property",
                                                                               ((VariableReference) property).astIdentifier().astValue()));
            }
        }
    }

    private boolean isSubclassOf(JavaContext context, VariableReference variableReference, String clazz) {
        JavaParser.ResolvedField resolvedVar = (JavaParser.ResolvedField) context.resolve(variableReference);
        if (resolvedVar == null) {
            return false;
        }
        JavaParser.TypeDescriptor type = resolvedVar.getType();
        if (type == null) {
            return false;
        }
        if (type.getTypeClass().isSubclassOf(clazz, false)) {
            return true;
        }
        return resolvedVar.getName().startsWith(clazz);
    }
}
