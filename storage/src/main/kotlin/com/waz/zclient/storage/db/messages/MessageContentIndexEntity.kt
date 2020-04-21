package com.waz.zclient.storage.db.messages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "MessageContentIndex")
data class MessageContentIndexEntity(

    @ColumnInfo(name = "message_id", defaultValue = "")
    val messageId: String,

    @ColumnInfo(name = "conv_id", defaultValue = "")
    val convId: String,

    @ColumnInfo(name = "content", defaultValue = "")
    val content: String,

    @ColumnInfo(name = "time", defaultValue = "0")
    val timestamp: Int
)
