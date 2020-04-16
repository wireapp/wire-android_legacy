package com.waz.zclient.storage.db.messages

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "Likings", primaryKeys = ["message_id", "user_id"])
data class LikesEntity(
    @ColumnInfo(name = "message_id", defaultValue = "")
    val messageId: String,

    @ColumnInfo(name = "user_id", defaultValue = "")
    val userId: String,

    @ColumnInfo(name = "timestamp", defaultValue = "0")
    val timeStamp: Int,

    @ColumnInfo(name = "action", defaultValue = "0")
    val action: Int
)
