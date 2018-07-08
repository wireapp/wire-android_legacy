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

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import lombok.ast.AstVisitor;
import lombok.ast.BinaryExpression;
import lombok.ast.BinaryOperator;
import lombok.ast.Expression;
import lombok.ast.FloatingPointLiteral;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.InlineIfExpression;
import lombok.ast.IntegralLiteral;
import lombok.ast.Literal;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.NullLiteral;
import lombok.ast.VariableReference;

import java.util.ArrayList;
import java.util.List;

public class MathUtilsDetector extends Detector implements Detector.JavaScanner {

    public static final Issue ISSUE_FLOAT_EQUALS = Issue.create(
        "com.waz.FloatEquals",
        "Use `MathUtils#floatEqual()` instead of comparing two floats directly",
        "See http://docs.oracle.com/cd/E19957-01/806-3568/ncg_goldberg.html for more information.",
        Category.CORRECTNESS,
        9,
        Severity.ERROR,
        new Implementation(MathUtilsDetector.class, Scope.JAVA_FILE_SCOPE));

    public MathUtilsDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        List<Class<? extends Node>> nodes = new ArrayList<>();
        nodes.add(BinaryExpression.class);
        return nodes;
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        if (!context.getProject().getReportIssues()) {
            return null;
        }
        return new FloatEqualVisitor(context);
    }

    private class FloatEqualVisitor extends ForwardingAstVisitor {

        private JavaContext context;

        FloatEqualVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitBinaryExpression(BinaryExpression node) {
            if (node == null) {
                return false;
            }
            if (!containsEqualityCheck(node)) {
                return false;
            }
            if (verifyExpression(node)) {
                context.report(ISSUE_FLOAT_EQUALS,
                               context.getLocation(node),
                               "Replace this comparison with `MathUtils#floatEqual()`");
            }
            return true;
        }

        private boolean containsEqualityCheck(BinaryExpression expression) {
            BinaryOperator operator = expression.astOperator();
            if (operator == BinaryOperator.EQUALS ||
                operator == BinaryOperator.NOT_EQUALS) {
                return true;
            }
            for (Node node : expression.getChildren()) {
                if (!(node instanceof BinaryExpression)) {
                    continue;
                }
                operator = ((BinaryExpression) node).astOperator();
                if (operator != BinaryOperator.EQUALS &&
                    operator != BinaryOperator.NOT_EQUALS) {
                    continue;
                }
                return true;
            }
            return false;
        }

        private boolean verifyExpression(@NonNull BinaryExpression node) {
            final BinaryOperator operator = node.astOperator();
            if (operator != BinaryOperator.EQUALS &&
                operator != BinaryOperator.NOT_EQUALS) {
                return false;
            }

            final Expression leftExpression = node.astLeft();
            final Expression rightExpression = node.astRight();

            final Literal leftType = getType(leftExpression);
            final Literal rightType = getType(rightExpression);

            if (leftType instanceof FloatingPointLiteral &&
                rightType instanceof FloatingPointLiteral) {
                return true;
            }

            if (leftType instanceof FloatingPointLiteral &&
                rightType instanceof IntegralLiteral) {
                return true;
            }

            return rightType instanceof FloatingPointLiteral &&
                leftType instanceof IntegralLiteral;
        }

        private Literal getType(Expression expression) {
            if (expression instanceof Literal) {
                return (Literal) expression;
            }

            if (expression instanceof VariableReference ||
                expression instanceof MethodInvocation) {
                JavaParser.ResolvedNode resolved = context.resolve(expression);
                JavaParser.TypeDescriptor descriptor;
                if (resolved instanceof JavaParser.ResolvedField) {
                    JavaParser.ResolvedField field = (JavaParser.ResolvedField) resolved;
                    descriptor = field.getType();
                } else if (resolved instanceof JavaParser.ResolvedVariable) {
                    JavaParser.ResolvedVariable field = (JavaParser.ResolvedVariable) resolved;
                    descriptor = field.getType();
                } else if (resolved instanceof JavaParser.ResolvedMethod) {
                    descriptor = ((JavaParser.ResolvedMethod) resolved).getReturnType();
                } else {
                    return new NullLiteral();
                }

                String type = descriptor.getName();
                Literal x = getLiteralForType(type);
                if (x != null) {
                    return x;
                }
            }

            if (expression instanceof InlineIfExpression) {
                return getType(((InlineIfExpression) expression).astIfTrue());
            }

            if (expression instanceof BinaryExpression) {
                return getType(((BinaryExpression) expression).astLeft());
            }

            return new NullLiteral();
        }

        private Literal getLiteralForType(String type) {
            if ("float".equals(type) ||
                "java.lang.Float".equals(type)) {
                return new FloatingPointLiteral();
            }

            if ("double".equals(type) ||
                "java.lang.Double".equals(type)) {
                return new FloatingPointLiteral();
            }

            if ("int".equals(type) ||
                "java.lang.Integer".equals(type)) {
                return new IntegralLiteral();
            }

            if ("long".equals(type) ||
                "java.lang.Long".equals(type)) {
                return new IntegralLiteral();
            }
            return null;
        }
    }
}
