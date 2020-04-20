package com.waz.zclient.storage.db.conversations

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Conversations",
    indices = [
        Index(name = "Conversation_search_key", value = ["search_key"])
    ]
)
data class ConversationsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "remote_id", defaultValue = "")
    val remoteId: String,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "creator", defaultValue = "")
    val creator: String,

    @ColumnInfo(name = "conv_type", defaultValue = "0")
    val conversationType: Int,

    @ColumnInfo(name = "team")
    val team: String?,

    @ColumnInfo(name = "is_managed")
    val managed: Boolean?,

    @ColumnInfo(name = "last_event_time", defaultValue = "0")
    val lastEventTime: Int,

    @ColumnInfo(name = "is_active", defaultValue = "0")
    val active: Boolean,

    @ColumnInfo(name = "last_read", defaultValue = "0")
    val lastRead: Int,

    @ColumnInfo(name = "muted_status", defaultValue = "0")
    val mutedStatus: Int,

    @ColumnInfo(name = "mute_time", defaultValue = "0")
    val muteTime: Int,

    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean,

    @ColumnInfo(name = "archive_time", defaultValue = "0")
    val archiveTime: Int,

    @ColumnInfo(name = "cleared")
    val cleared: Int?,

    @ColumnInfo(name = "generated_name", defaultValue = "")
    val generatedName: String,

    @ColumnInfo(name = "search_key")
    val searchKey: String?,

    @ColumnInfo(name = "unread_count", defaultValue = "0")
    val unreadCount: Int,

    @ColumnInfo(name = "unsent_count", defaultValue = "0")
    val unsentCount: Int,

    @ColumnInfo(name = "hidden", defaultValue = "0")
    val hidden: Boolean,

    @ColumnInfo(name = "missed_call")
    val missedCall: String?,

    @ColumnInfo(name = "incoming_knock")
    val incomingKnock: String?,

    @ColumnInfo(name = "verified")
    val verified: String?,

    @ColumnInfo(name = "ephemeral")
    val ephemeral: Int?,

    @ColumnInfo(name = "global_ephemeral")
    val globalEphemeral: Int?,

    @ColumnInfo(name = "unread_call_count", defaultValue = "0")
    val unreadCallCount: Int,

    @ColumnInfo(name = "unread_ping_count", defaultValue = "0")
    val unreadPingCount: Int,

    @ColumnInfo(name = "access")
    val access: String?,

    @ColumnInfo(name = "access_role")
    val accessRole: String?,

    @ColumnInfo(name = "link")
    val link: String?,

    @ColumnInfo(name = "unread_mentions_count", defaultValue = "0")
    val unreadMentionsCount: Int,

    @ColumnInfo(name = "unread_quote_count", defaultValue = "0")
    val unreadQuoteCount: Int,

    @ColumnInfo(name = "receipt_mode")
    val receiptMode: Int?
)
