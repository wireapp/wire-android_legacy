package com.waz.zclient.storage.db.messages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "MessageContentIndex")
data class MessageContentIndexEntity(

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "conv_id")
    val convId: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "time")
    val timestamp: Int
)
