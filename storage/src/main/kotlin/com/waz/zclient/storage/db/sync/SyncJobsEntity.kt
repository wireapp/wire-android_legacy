package com.waz.zclient.storage.db.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "SyncJobs")
data class SyncJobsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "data", defaultValue = "")
    val data: String
)
