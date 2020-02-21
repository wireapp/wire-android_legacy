package com.waz.zclient.storage.db.messagedeletion

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "MsgDeletion", primaryKeys = ["message_id", "timestamp"])
data class MessageDeletionEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Int
)
