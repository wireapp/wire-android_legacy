package com.waz.zclient.storage.db.folders

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Folders")
data class FoldersEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "name")
    var name: String?,

    @ColumnInfo(name = "type")
    var type: Int?
)
