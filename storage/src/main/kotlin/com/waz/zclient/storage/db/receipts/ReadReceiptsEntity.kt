package com.waz.zclient.storage.db.receipts

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "ReadReceipts", primaryKeys = ["message_id", "user_id"])
data class ReadReceiptsEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Int
)
