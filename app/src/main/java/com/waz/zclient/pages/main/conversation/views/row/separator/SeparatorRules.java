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
package com.waz.zclient.pages.main.conversation.views.row.separator;

import com.waz.api.Message;
import com.waz.zclient.utils.DateConvertUtils;
import org.threeten.bp.ZonedDateTime;
import timber.log.Timber;

public class SeparatorRules {

    private static final String TAG = SeparatorRules.class.getName();

    public static boolean shouldHaveName(Message message, Separator separator) {
        if (message.isEdited()) {
            return true;
        }

        // First message with no previous messages e.g. when history has been cleared
        if (separator.previousMessage == null &&
                separator.nextMessage.getMessageType() != Message.Type.MEMBER_JOIN &&
                separator.nextMessage.getMessageType() != Message.Type.MEMBER_LEAVE &&
                separator.nextMessage.getMessageType() != Message.Type.CONNECT_REQUEST &&
                separator.nextMessage.getMessageType() != Message.Type.KNOCK &&
                separator.nextMessage.getMessageType() != Message.Type.RENAME &&
                separator.nextMessage.getMessageType() != Message.Type.OTR_ERROR &&
                separator.nextMessage.getMessageType() != Message.Type.OTR_VERIFIED &&
                separator.nextMessage.getMessageType() != Message.Type.OTR_UNVERIFIED &&
                separator.nextMessage.getMessageType() != Message.Type.STARTED_USING_DEVICE &&
                separator.nextMessage.getMessageType() != Message.Type.OTR_DEVICE_ADDED &&
                separator.nextMessage.getMessageType() != Message.Type.HISTORY_LOST &&
                separator.nextMessage.getMessageType() != Message.Type.MISSED_CALL) {
            return true;
        }

        return separator.previousMessage != null &&

               // messages are from different users, or the previous message is not text or image or rich media
               (!separator.previousMessage.getUser().getId().equals(separator.nextMessage.getUser().getId()) ||
                (separator.previousMessage.getUser().getId().equals(separator.nextMessage.getUser().getId()) &&
                 separator.previousMessage.getMessageType() != Message.Type.ASSET &&
                 separator.previousMessage.getMessageType() != Message.Type.LOCATION &&
                 separator.previousMessage.getMessageType() != Message.Type.TEXT &&
                 separator.previousMessage.getMessageType() != Message.Type.RICH_MEDIA
                ) ||
                separator.nextMessage.getMessageType() == Message.Type.ANY_ASSET ||
                separator.nextMessage.getMessageType() == Message.Type.RECALLED
               ) &&

               //next message is not a "system" message or a knock
                separator.nextMessage.getMessageType() != Message.Type.MEMBER_JOIN &&
                separator.nextMessage.getMessageType() != Message.Type.MEMBER_LEAVE &&
                separator.nextMessage.getMessageType() != Message.Type.CONNECT_REQUEST &&
                separator.nextMessage.getMessageType() != Message.Type.KNOCK &&
                separator.nextMessage.getMessageType() != Message.Type.RENAME &&
                separator.nextMessage.getMessageType() != Message.Type.OTR_ERROR &&
                separator.nextMessage.getMessageType() != Message.Type.OTR_VERIFIED &&
                separator.nextMessage.getMessageType() != Message.Type.OTR_UNVERIFIED &&
                separator.nextMessage.getMessageType() != Message.Type.STARTED_USING_DEVICE &&
                separator.nextMessage.getMessageType() != Message.Type.OTR_DEVICE_ADDED &&
                separator.nextMessage.getMessageType() != Message.Type.HISTORY_LOST &&
                separator.nextMessage.getMessageType() != Message.Type.MISSED_CALL;
    }

    public static boolean shouldHaveTimestamp(Separator separator, int timeBetweenMessagesToTriggerTimestamp) {
        if (separator.previousMessage == null || separator.nextMessage == null) {
            return false;
        }

        if (separator.nextMessage.getMessageType() == Message.Type.MISSED_CALL) {
            return false;
        }

        try {
            ZonedDateTime previousMessageTime = DateConvertUtils.asZonedDateTime(separator.previousMessage.getTime());
            ZonedDateTime nextMessageTIme = DateConvertUtils.asZonedDateTime(separator.nextMessage.getTime());
            return previousMessageTime.isBefore(nextMessageTIme.minusSeconds(timeBetweenMessagesToTriggerTimestamp));
        } catch (Exception e) {
            Timber.e(e, "Failed Separator timestamp check! Couldn't parse received time: either '%s' or '%s', or both.",
                separator.previousMessage.getTime(),
                separator.nextMessage.getTime());
            return false;
        }
    }

    public static boolean shouldHaveUnreadDot(Separator separator, int unreadMessageCount) {
        return unreadMessageCount > 0 &&
               separator.lastReadMessage != null &&
               separator.previousMessage != null &&
               !separator.previousMessage.getId().equals(separator.nextMessage.getId()) &&
               separator.lastReadMessage.getId().equals(separator.previousMessage.getId());
    }

    public static boolean shouldHaveBigTimestamp(Separator separator) {
        if (separator.previousMessage == null || separator.nextMessage == null) {
            return false;
        }

        try {
            ZonedDateTime previousMessageTime = DateConvertUtils.asZonedDateTime(separator.previousMessage.getTime());
            ZonedDateTime nextMessageTime = DateConvertUtils.asZonedDateTime(separator.nextMessage.getTime());
            return previousMessageTime.toLocalDate().atStartOfDay().isBefore(nextMessageTime.toLocalDate().atStartOfDay());
        } catch (Exception e) {
            Timber.e(e, "Failed Separator timestamp check! Couldn't parse received time: either '%s' or '%s', or both.",
                     separator.previousMessage.getTime(),
                     separator.nextMessage.getTime());
            return false;
        }
    }
}
