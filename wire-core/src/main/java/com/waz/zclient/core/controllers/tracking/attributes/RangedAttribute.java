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
package com.waz.zclient.core.controllers.tracking.attributes;

public enum RangedAttribute {
    CONNECT_REQUESTS_SENT("connectRequestsSent", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    CONNECT_REQUESTS_ACCEPTED("connectRequestsAccepted", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    CONNECT_REQUEST_SHARED_CONTACTS("shared_users", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    VOICE_CALLS_INITIATED("voiceCallsInitiated", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    INCOMING_CALLS_ACCEPTED("incomingCallsAccepted", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    INCOMING_CALLS_SILENCED("incomingCallsSilenced", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    GROUP_CONVERSATIONS_STARTED("groupConversationsStarted", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    USERS_ADDED_TO_CONVERSATIONS("usersAddedToConversations", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    YOUTUBE_LINKS_SENT("youtubeLinksSent", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    SOUNDCLOUD_LINKS_SENT("soundcloudLinksSent", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    PINGS_SENT("pingsSent", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    IMAGE_CONTENT_CLICKS("imageClicks", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    SOUNDCLOUD_CONTENT_CLICKS("soundcloudClicks", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    SPOTIFY_CONTENT_CLICKS("spotifyClicks", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    YOUTUBE_CONTENT_CLICKS("youtubeClicks", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    CONVERSATION_RENAMES("convesationsRenamed", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    SESSION_DURATION("sessionDuration", new int[] {0, 1, 5, 10, 20, 30, 60, 120, 180, 300, 600, 1200, 1800, 2400, 3000, 3600}),
    OPENED_SEARCH("openedSearch", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    NUMBER_OF_VOICE_CALLS("voiceCalls", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50, 100}),
    NUMBER_OF_VIDEO_CALLS("videoCalls", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50, 100}),
    TEXT_MESSAGES_SENT("textMessagesSent", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50, 100}),
    IMAGES_SENT("imagesSent", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50, 100}),
    NUMBER_OF_CONTACTS("numberOfContacts", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    NUMBER_OF_GROUP_CONVERSATIONS("numberOfGroupConversations", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    NUMBER_OF_CONTACTS_ADDED("numberOfContactsAdded", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    MEMBERS("members", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    // [0s-15s], [16s-30s], [31s-60s], [61s-3min], [3min-10min], [10min-1h], [1h-infinite]
    VOICE_CALL_DURATION("duration", new int[] {0, 16, 31, 61, 181, 601, 1200, 3601}),
    CALLING_SETUP_TIME("setup_time", new int[] {0, 1, 2, 3, 4, 5, 10, 30}),
    FILE_SIZE_MB("size_mb", new int[] {0, 1, 6, 11, 16, 21, 26}),
    FILE_UPLOAD_DURATION("duration", new int[] {0, 6, 11, 21, 31, 61, 181, 301}),
    VIDEO_AND_AUDIO_MESSAGE_DURATION("duration", new int[] {0, 1, 11, 31, 61, 301, 901, 1801}),
    MESSAGE_ACTION_TIME_ELAPSED("time_elapsed", new int[] {0, 1, 61, 301, 601, 1801, 3601, 86401}),
    NUMBER_OF_ARCHIVED_CONVERSATIONS("numberOfArchivedConversations", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50}),
    NUMBER_OF_MUTED_CONVERSATIONS("numberOfMutedConversations", new int[] {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 40, 50});

    public final String name;
    public final String actualValueName;
    public final int[] rangeSteps;

    RangedAttribute(String name, int[] rangeSteps) {
        this.name = name;
        this.rangeSteps = rangeSteps;
        this.actualValueName = name + "Actual";
    }
}
