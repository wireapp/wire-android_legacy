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
package com.waz.zclient.core.stores.conversation;

public enum ConversationChangeRequester {
    FIRST_LOAD,
    START_CONVERSATION,
   // TODO: Remove when call issue is resolved with SE CM-675
    START_CONVERSATION_FOR_CALL,
    START_CONVERSATION_FOR_VIDEO_CALL,
    START_CONVERSATION_FOR_CAMERA,
    LEAVE_CONVERSATION,
    DELETE_CONVERSATION,
    UPDATER,
    CONVERSATION_LIST,
    CONVERSATION_LIST_UNARCHIVED_CONVERSATION,
    CONVERSATION_LIST_SELECT_TO_SHARE,
    SELF_PROFILE,
    INBOX,
    BLOCK_USER,
    ONGOING_CALL,
    TRANSFER_CALL,
    ARCHIVED_RESULT,
    INCOMING_CALL,
    INVITE,
    INTENT,
    CONNECT_REQUEST_ACCEPTED,
    CONNECT_REQUEST_IGNORED,
    CONNECT_REQUEST_CANCELLED,
    ACCOUNT_CHANGE
}
