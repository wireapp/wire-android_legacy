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
package com.waz.zclient.pages.main.profile.validator;

import android.text.TextUtils;

public class EmailValidator implements Validator {

    private AcceptMode acceptMode;

    private EmailValidator(AcceptMode acceptMode) {
        this.acceptMode = acceptMode;
    }

    public static EmailValidator newInstance() {
        return new EmailValidator(AcceptMode.STRICT);
    }

    public static EmailValidator newInstanceAcceptingEmptyString() {
        return new EmailValidator(AcceptMode.EMPTY_STRING);
    }

    public static EmailValidator newInstanceAcceptingEverything() {
        return new EmailValidator(AcceptMode.ALL);
    }

    public boolean validate(String email) {
        if (acceptMode == AcceptMode.ALL) {
            return true;
        }

        if (acceptMode == AcceptMode.EMPTY_STRING &&
            TextUtils.isEmpty(email)) {
            return true;
        }

        if (TextUtils.isEmpty(email)) {
            return false;
        }

        if (email.length() < 5) {
            return false;
        }

        if (!(email.contains("@") && email.contains("."))) {
            return false;
        }

        return !(email.lastIndexOf('@') > email.lastIndexOf('.'));
    }

    @Override
    public boolean invalidate(String text) {
        if (!TextUtils.isEmpty(text)) {
            return text.length() > 1;
        }
        return false;
    }
}
