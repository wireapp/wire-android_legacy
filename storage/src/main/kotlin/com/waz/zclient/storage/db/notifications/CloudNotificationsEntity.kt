package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "FCMNotifications", primaryKeys = ["_id", "stage"])
data class CloudNotificationsEntity(
    @ColumnInfo(name = "_id", defaultValue = "")
    val id: String,

    @ColumnInfo(name = "stage", defaultValue = "")
    val stage: String,

    @ColumnInfo(name = "stage_start_time", defaultValue = "0")
    val stageStartTime: Int
)
