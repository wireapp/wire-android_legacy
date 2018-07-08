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

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.utils.ScopeUtils;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

public class NamingConventionCheck extends Check {

    private static final String MSG_CONSTANT = "Constant '%s' should be named in all uppercase with underscores.";
    private static final String MSG_UNDERSCORE = "Underscores are not allowed in non-constant variable names. Use camelCase for '%s'.";
    private static final String MSG_STATIC = "Static variable '%s' should not begin with \"s\"";
    private static final String MSG_MEMBER = "Member variable '%s' should not begin with \"m\"";
    private static final String MSG_UPPERCASE = "Variable name '%s' should begin with a lower-case letter.";
    private static final String MSG_PREFIX = "Non-member variable '%s' should not be prefixed.";

    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.VARIABLE_DEF};
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {TokenTypes.VARIABLE_DEF};
    }

    @Override
    public void visitToken(DetailAST ast) {
        String name = findVariableName(ast);
        if ("serialVersionUID".equals(name)) {
            return;
        }

        if (ScopeUtils.isInInterfaceOrAnnotationBlock(ast)) {
            return;
        }

        final DetailAST modifiersAST = ast.findFirstToken(TokenTypes.MODIFIERS);
        if (isStaticVariable(modifiersAST) &&
            isFinalVariable(modifiersAST)) {
            if (NamingConventionsDetector.detectsWrongConstantNaming(name)) {
                reportStyleError(ast, MSG_CONSTANT, name);
            }
        } else {
            if (NamingConventionsDetector.detectsUnderscore(name)) {
                reportStyleError(ast, MSG_UNDERSCORE, name);
                return;
            }

            if (isStaticVariable(modifiersAST) &&
                NamingConventionsDetector.detectsStaticPrefix(name)) {
                reportStyleError(ast, MSG_STATIC, name);
                return;
            }

            if ((isProtectedVariable(modifiersAST) || isPrivateVariable(modifiersAST)) &&
                NamingConventionsDetector.detectsMemberPrefix(name)) {
                reportStyleError(ast, MSG_MEMBER, name);
                return;
            }

            if (NamingConventionsDetector.detectsUpperCaseBeginning(name)) {
                reportStyleError(ast, MSG_UPPERCASE, name);
                return;
            }

            if (NamingConventionsDetector.detectsPrefix(name)) {
                reportStyleError(ast, MSG_PREFIX, name);
            }
        }
    }

    private boolean isPrivateVariable(DetailAST ast) {
        return ast.branchContains(TokenTypes.LITERAL_PRIVATE);
    }

    private boolean isProtectedVariable(DetailAST ast) {
        return ast.branchContains(TokenTypes.LITERAL_PROTECTED);
    }

    private boolean isFinalVariable(DetailAST ast) {
        return ast.branchContains(TokenTypes.FINAL);
    }

    private boolean isStaticVariable(DetailAST ast) {
        return ast.branchContains(TokenTypes.LITERAL_STATIC);
    }

    private String findVariableName(DetailAST ast) {
        return ast.findFirstToken(TokenTypes.IDENT).getText();
    }

    private void reportStyleError(DetailAST ast, String msg, String variableName) {
        log(ast.getLineNo(), ast.getColumnNo(), String.format(msg, variableName));
    }
}
