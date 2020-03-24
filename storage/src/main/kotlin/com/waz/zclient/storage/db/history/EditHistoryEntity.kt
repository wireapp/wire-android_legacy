package com.waz.zclient.storage.db.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "EditHistory")
class EditHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "original_id")
    val originalId: String,

    @ColumnInfo(name = "updated_id")
    val updatedId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Int
)
