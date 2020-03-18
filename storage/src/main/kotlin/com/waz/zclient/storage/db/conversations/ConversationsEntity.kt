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

    @ColumnInfo(name = "remote_id")
    val remoteId: String,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "creator")
    val creator: String,

    @ColumnInfo(name = "conv_type")
    val conversationType: Int,

    @ColumnInfo(name = "team")
    val team: String?,

    @ColumnInfo(name = "is_managed")
    val managed: Int?,

    @ColumnInfo(name = "last_event_time")
    val lastEventTime: Int,

    @ColumnInfo(name = "is_active")
    val active: Int,

    @ColumnInfo(name = "last_read")
    val lastRead: Int,

    @ColumnInfo(name = "muted_status")
    val mutedStatus: Int,

    @ColumnInfo(name = "mute_time")
    val muteTime: Int,

    @ColumnInfo(name = "archived")
    val archived: Int,

    @ColumnInfo(name = "archive_time")
    val archiveTime: Int,

    @ColumnInfo(name = "cleared")
    val cleared: Int?,

    @ColumnInfo(name = "generated_name")
    val generatedName: String,

    @ColumnInfo(name = "search_key")
    val searchKey: String?,

    @ColumnInfo(name = "unread_count")
    val unreadCount: Int,

    @ColumnInfo(name = "unsent_count")
    val unsentCount: Int,

    @ColumnInfo(name = "hidden")
    val hidden: Int,

    @ColumnInfo(name = "missed_call")
    val missedCall: String?,

    @ColumnInfo(name = "incoming_knock")
    val incomingKnock: String?,

    @ColumnInfo(name = "verified")
    val verified: String,

    @ColumnInfo(name = "ephemeral")
    val ephemeral: Int?,

    @ColumnInfo(name = "global_ephemeral")
    val globalEphemeral: Int?,

    @ColumnInfo(name = "unread_call_count")
    val unreadCallCount: Int,

    @ColumnInfo(name = "unread_ping_count")
    val unreadPingCount: Int,

    @ColumnInfo(name = "access")
    val access: String,

    @ColumnInfo(name = "access_role")
    val accessRole: String?,

    @ColumnInfo(name = "link")
    val link: String?,

    @ColumnInfo(name = "unread_mentions_count")
    val unreadMentionsCount: Int,

    @ColumnInfo(name = "unread_quote_count")
    val unreadQuoteCount: Int,

    @ColumnInfo(name = "receipt_mode")
    val receiptMode: Int?
)
