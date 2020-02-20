package com.waz.zclient.storage.db.conversations

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "EditHistory")
class EditHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "original_id")
    val originalId: String,

    @ColumnInfo(name = "updated_id")
    var updatedId: String?,

    @ColumnInfo(name = "timestamp")
    var timestamp: Int?
)
