package com.waz.zclient.storage.db.conversations

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts3
import androidx.room.PrimaryKey

@Fts3
@Entity(tableName = "MessageContentIndex")
data class MessageContentIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "conv_id")
    val convId: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "time")
    val timestamp: Int
)
