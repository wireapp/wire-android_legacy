package com.waz.zclient.storage.userdatabase.conversations

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class ConversationsTableTestHelper private constructor() {


    companion object {

        private const val CONVERSATIONS_TABLE_NAME = "Conversations"
        private const val CONVERSATION_ID_COL = "_id"
        private const val REMOTE_ID_COL = "remote_id"
        private const val NAME_COL = "name"
        private const val CREATOR_COL = "creator"
        private const val CONVERSATION_TYPE_COL = "conv_type"
        private const val TEAM_COL = "team"
        private const val IS_MANAGED_COL = "is_managed"
        private const val LAST_EVENT_TIME_COL = "last_event_time"
        private const val IS_ACTIVE_COL = "is_active"
        private const val LAST_READ_COL = "last_read"
        private const val MUTED_STATUS_COL = "muted_status"
        private const val MUTE_TIME_COL = "mute_time"
        private const val ARCHIVED_COL = "archived"
        private const val ARCHIVE_TIME_COL = "archive_time"
        private const val CLEARED_COL = "cleared"
        private const val GENERATED_NAME_COL = "generated_name"
        private const val SEARCH_KEY_COL = "search_key"
        private const val UNREAD_COUNT_COL = "unread_count"
        private const val UNSENT_COUNT_COL = "unsent_count"
        private const val HIDDEN_COL = "hidden"
        private const val MISSED_CALL_COL = "missed_call"
        private const val INCOMING_KNOCK_COL = "incoming_knock"
        private const val VERIFIED_COL = "verified"
        private const val EPHEMERAL_COL = "ephemeral"
        private const val GLOBAL_EPHEMERAL_COL = "global_ephemeral"
        private const val UNREAD_CALL_COUNT_COL = "unread_call_count"
        private const val UNREAD_PING_COUNT_COL = "unread_ping_count"
        private const val ACCESS_COL = "access"
        private const val ACCESS_ROLE_COL = "access_role"
        private const val LINK_COL = "link"
        private const val UNREAD_MENTIONS_COUNT = "unread_mentions_count"
        private const val UNREAD_QUOTE_COUNT = "unread_quote_count"
        private const val RECEIPT_MODE = "receipt_mode"

        fun insertConversation(
            id: String,
            remoteId: String,
            name: String?,
            creator: String,
            conversationType: Int,
            team: String?,
            managed: Boolean?,
            lastEventTime: Int,
            active: Boolean,
            lastRead: Int,
            mutedStatus: Int,
            muteTime: Int,
            archived: Boolean,
            archiveTime: Int,
            cleared: Int?,
            generatedName: String,
            searchKey: String?,
            unreadCount: Int,
            unsentCount: Int,
            hidden: Boolean,
            missedCall: String?,
            incomingKnock: String?,
            verified: String,
            ephemeral: Int?,
            globalEphemeral: Int?,
            unreadCallCount: Int,
            unreadPingCount: Int,
            access: String?,
            accessRole: String?,
            link: String?,
            unreadMentionsCount: Int,
            unreadQuoteCount: Int,
            receiptMode: Int?,
            openHelper: DbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(CONVERSATION_ID_COL, id)
                it.put(REMOTE_ID_COL, remoteId)
                it.put(NAME_COL, name)
                it.put(CREATOR_COL, creator)
                it.put(CONVERSATION_TYPE_COL, conversationType)
                it.put(TEAM_COL, team)
                it.put(IS_MANAGED_COL, managed)
                it.put(LAST_EVENT_TIME_COL, lastEventTime)
                it.put(IS_ACTIVE_COL, active)
                it.put(LAST_READ_COL, lastRead)
                it.put(MUTED_STATUS_COL, mutedStatus)
                it.put(MUTE_TIME_COL, muteTime)
                it.put(ARCHIVED_COL, archived)
                it.put(ARCHIVE_TIME_COL, archiveTime)
                it.put(CLEARED_COL, cleared)
                it.put(GENERATED_NAME_COL, generatedName)
                it.put(SEARCH_KEY_COL, searchKey)
                it.put(UNREAD_COUNT_COL, unreadCount)
                it.put(UNSENT_COUNT_COL, unsentCount)
                it.put(HIDDEN_COL, hidden)
                it.put(MISSED_CALL_COL, missedCall)
                it.put(INCOMING_KNOCK_COL, incomingKnock)
                it.put(VERIFIED_COL, verified)
                it.put(EPHEMERAL_COL, ephemeral)
                it.put(GLOBAL_EPHEMERAL_COL, globalEphemeral)
                it.put(UNREAD_CALL_COUNT_COL, unreadCallCount)
                it.put(UNREAD_PING_COUNT_COL, unreadPingCount)
                it.put(ACCESS_COL, access)
                it.put(ACCESS_ROLE_COL, accessRole)
                it.put(UNREAD_MENTIONS_COUNT, unreadMentionsCount)
                it.put(LINK_COL, link)
                it.put(UNREAD_QUOTE_COUNT, unreadQuoteCount)
                it.put(RECEIPT_MODE, receiptMode)
            }

            openHelper.insertWithOnConflict(
                tableName = CONVERSATIONS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
