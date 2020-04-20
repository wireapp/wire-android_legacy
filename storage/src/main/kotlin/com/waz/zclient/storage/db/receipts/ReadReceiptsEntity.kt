package com.waz.zclient.storage.db.receipts

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "ReadReceipts", primaryKeys = ["message_id", "user_id"])
data class ReadReceiptsEntity(
    @ColumnInfo(name = "message_id", defaultValue = "")
    val messageId: String,

    @ColumnInfo(name = "user_id", defaultValue = "")
    val userId: String,

    @ColumnInfo(name = "timestamp", defaultValue = "0")
    val timestamp: Int
)
