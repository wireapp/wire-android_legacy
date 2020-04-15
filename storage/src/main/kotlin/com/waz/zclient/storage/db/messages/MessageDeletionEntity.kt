package com.waz.zclient.storage.db.messages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "MsgDeletion")
data class MessageDeletionEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "timestamp", defaultValue = "0")
    val timestamp: Int
)
