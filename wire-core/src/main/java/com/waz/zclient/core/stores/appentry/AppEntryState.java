/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.core.stores.appentry;

import java.util.EnumSet;

public enum AppEntryState {

    // register with phone screen
    PHONE_REGISTER,

    // sign in with phone screen
    PHONE_SIGN_IN,

    // auto-read code from SMS
    PHONE_SET_CODE,

    // phone + code validated, previously registered phone number
    PHONE_SIGNED_IN,

    // phone + code validated, previously registered phone number - resuming sign-in from BG
    // This state is needed because SyncEngine can't reliably know whether an email was verified.
    PHONE_SIGNED_IN_RESUMING,

    // enter email and password, mandatory
    PHONE_EMAIL_PASSWORD,

    // verify email and password
    PHONE_VERIFY_EMAIL,

    // phone + code validated, new phone number
    PHONE_SET_NAME,

    // take a picture screen
    PHONE_SET_PICTURE,

    // tablet welcome screen
    EMAIL_WELCOME,

    // register with email screen
    EMAIL_REGISTER,

    // sign in with email screen
    EMAIL_SIGN_IN,

    // verify email
    EMAIL_VERIFY_EMAIL,

    // take a picture
    EMAIL_SET_PICTURE,

    // optional: enter phone number
    EMAIL_SET_PHONE,

    // optional: enter phone number code
    EMAIL_SET_CODE,

    // email validated, ask for phone (if there isn't one), and picture (if there isn't one)
    EMAIL_SIGNED_IN,

    // ready to rock, show MainActivity
    LOGGED_IN,

    // Personal email invitation token found - one click signup
    EMAIL_INVITATION,

    // Personal phone invitation token found - one click signup
    PHONE_INVITATION,

    // Logged in for the first time, show message about no message history
    FIRST_LOGIN;

    public static EnumSet<AppEntryState> entryPoints() {
        return EnumSet.of(AppEntryState.PHONE_REGISTER,
                          AppEntryState.PHONE_SIGN_IN,
                          AppEntryState.PHONE_SET_NAME,
                          AppEntryState.EMAIL_REGISTER,
                          AppEntryState.EMAIL_SIGN_IN);
    }
}
