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

import android.content.Context;
import android.text.TextUtils;
import com.waz.zclient.R;

public class PasswordValidator implements Validator {

    private final AcceptMode acceptMode;
    private final Context context;

    private PasswordValidator(Context context, AcceptMode acceptMode) {
        this.acceptMode = acceptMode;
        this.context = context;
    }

    public static PasswordValidator instance(Context context) {
        return new PasswordValidator(context, AcceptMode.STRICT);
    }

    public static PasswordValidator instanceAcceptingEmptyString(Context context) {
        return new PasswordValidator(context, AcceptMode.EMPTY_STRING);
    }

    public static PasswordValidator instanceAcceptingEverything(Context context) {
        return new PasswordValidator(context, AcceptMode.ALL);
    }

    @Override
    public boolean validate(String text) {
        if (acceptMode == AcceptMode.ALL) {
            return true;
        }

        if (acceptMode == AcceptMode.EMPTY_STRING && TextUtils.isEmpty(text)) {
            return true;
        }

        return !TextUtils.isEmpty(text);
    }

    @Override
    public boolean invalidate(String text) {
        return !TextUtils.isEmpty(text);
    }
}
