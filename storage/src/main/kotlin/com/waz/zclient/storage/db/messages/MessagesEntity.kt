package com.waz.zclient.storage.db.messages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Messages",
    indices = [Index(name = "Messages_conv_time", value = ["conv_id", "time"])]
)
data class MessagesEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "conv_id", defaultValue = "")
    val conversationId: String,

    @ColumnInfo(name = "msg_type", defaultValue = "")
    val messageType: String,

    @ColumnInfo(name = "user_id", defaultValue = "")
    val userId: String,

    @ColumnInfo(name = "content")
    val content: String?,

    @ColumnInfo(name = "protos", typeAffinity = ColumnInfo.BLOB)
    val protos: ByteArray?,

    @ColumnInfo(name = "time", defaultValue = "0")
    val time: Int,

    @ColumnInfo(name = "first_msg", defaultValue = "0")
    val firstMessage: Boolean,

    @ColumnInfo(name = "members")
    val members: String?,

    @ColumnInfo(name = "recipient")
    val recipient: String?,

    @ColumnInfo(name = "email")
    val email: String?,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "msg_state", defaultValue = "")
    val messageState: String,

    @ColumnInfo(name = "content_size", defaultValue = "0")
    val contentSize: Int,

    @ColumnInfo(name = "local_time", defaultValue = "0")
    val localTime: Int,

    @ColumnInfo(name = "edit_time", defaultValue = "0")
    val editTime: Int,

    @ColumnInfo(name = "ephemeral")
    val ephemeral: Int?,

    @ColumnInfo(name = "expiry_time")
    val expiryTime: Int?,

    @ColumnInfo(name = "expired", defaultValue = "0")
    val expired: Boolean,

    @ColumnInfo(name = "duration")
    val duration: Int?,

    @ColumnInfo(name = "quote")
    val quote: String?,

    @ColumnInfo(name = "quote_validity", defaultValue = "0")
    val quoteValidity: Int,

    @ColumnInfo(name = "force_read_receipts")
    val forceReadReceipts: Int?,

    @ColumnInfo(name = "asset_id")
    val assetId: String?
)
