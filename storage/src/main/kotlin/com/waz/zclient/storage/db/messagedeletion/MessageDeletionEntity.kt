package com.waz.zclient.storage.db.messagedeletion

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "MsgDeletion")
data class MessageDeletionEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Int
)
