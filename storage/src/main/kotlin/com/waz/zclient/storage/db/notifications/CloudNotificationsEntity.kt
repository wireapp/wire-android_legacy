package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "FCMNotifications", primaryKeys = ["_id", "stage"])
data class CloudNotificationsEntity(
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "stage")
    val stage: String,

    @ColumnInfo(name = "stage_start_time")
    val stageStartTime: Int
)
