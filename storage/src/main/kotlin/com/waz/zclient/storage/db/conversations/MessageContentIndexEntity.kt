package com.waz.zclient.storage.db.conversations

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "MessageContentIndex")
data class MessageContentIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "conv_id")
    var convId: String?,

    @ColumnInfo(name = "content")
    var content: String?,

    @ColumnInfo(name = "time")
    var timestamp: Int?
)
