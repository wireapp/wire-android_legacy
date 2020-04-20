package com.waz.zclient.storage.db.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "EditHistory")
data class EditHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "original_id")
    val originalId: String,

    @ColumnInfo(name = "updated_id", defaultValue = "")
    val updatedId: String,

    @ColumnInfo(name = "timestamp", defaultValue = "0")
    val timestamp: Int
)
