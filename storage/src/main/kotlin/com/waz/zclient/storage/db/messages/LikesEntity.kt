package com.waz.zclient.storage.db.messages

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "Likings", primaryKeys = ["message_id", "user_id"])
data class LikesEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "timestamp")
    val timeStamp: Int,

    @ColumnInfo(name = "action")
    val action: Int
)
