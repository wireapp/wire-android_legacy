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
package com.waz.zclient.controllers.tracking.screens;

public enum ApplicationScreen {
    DISABLED_TRACKING,
    PHONE_REGISTRATION__VERIFY_CODE,
    PHONE_REGISTRATION__ADD_NAME,
    REGISTRATION__ADD_PHOTO,
    LOGIN_REGISTRATION,

    CONVERSATION_LIST,
    START_UI,
    START_UI__ADD_TO_CONVERSATION,
    SEND_GENERIC_INVITE_MENU,
    SEND_PERSONAL_INVITE_MENU,
    USER_SELF_PROFILE,
    USER_SELF_PROFILE__OPTIONS_MENU,
    CONVERSATION,
    CONVERSATION__INBOX,
    CONVERSATION__PENDING,
    CONVERSATION__BOT,
    CONVERSATION__PARTICIPANTS,
    CAMERA,
    DRAW_SKETCH
}
