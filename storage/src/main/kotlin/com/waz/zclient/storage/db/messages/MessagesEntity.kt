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

    @ColumnInfo(name = "conv_id")
    val conversationId: String,

    @ColumnInfo(name = "msg_type")
    val messageType: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "content")
    val content: String?,

    //TODO: override equals/hashcode?
    @ColumnInfo(name = "protos", typeAffinity = ColumnInfo.BLOB) val protos: ByteArray?,

    @ColumnInfo(name = "time")
    val time: Int,

    @ColumnInfo(name = "local_time")
    val localTime: Int,

    @ColumnInfo(name = "first_msg")
    val firstMessage: Int,

    @ColumnInfo(name = "members")
    val members: String?,

    @ColumnInfo(name = "recipient")
    val recipient: String?,

    @ColumnInfo(name = "email")
    val email: String?,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "msg_state")
    val messageState: String,

    @ColumnInfo(name = "content_size")
    val contentSize: Int,

    @ColumnInfo(name = "edit_time")
    val editTime: Int,

    @ColumnInfo(name = "ephemeral")
    val ephemeral: Int?,

    @ColumnInfo(name = "expiry_time")
    val expiryTime: Int?,

    @ColumnInfo(name = "expired")
    val expired: Int,

    @ColumnInfo(name = "duration")
    val duration: Int?,

    @ColumnInfo(name = "quote")
    val quote: String?,

    @ColumnInfo(name = "quote_validity")
    val quoteValidity: Int,

    @ColumnInfo(name = "force_read_receipts")
    val forceReadReceipts: Int?,

    @ColumnInfo(name = "asset_id")
    val assetId: String?
)
