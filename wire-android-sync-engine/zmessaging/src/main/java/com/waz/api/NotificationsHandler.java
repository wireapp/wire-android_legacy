/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.api;

public interface NotificationsHandler {

    enum NotificationType {
        CONNECT_REQUEST,
        CONNECT_ACCEPTED,
        IMAGE_ASSET,
        ANY_ASSET,
        VIDEO_ASSET,
        AUDIO_ASSET,
        TEXT,
        RENAME,
        KNOCK,
        MISSED_CALL,
        LIKE,
        LOCATION,
        MESSAGE_SENDING_FAILED,
        CONVERSATION_DELETED,
        COMPOSITE;

        public enum LikedContent {
            TEXT_OR_URL, // the text or URL is contained in #getMessage in this case
            PICTURE,
            OTHER
        }
    }
}
