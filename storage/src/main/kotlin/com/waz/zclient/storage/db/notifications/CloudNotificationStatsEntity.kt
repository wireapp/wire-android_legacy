package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "FCMNotificationStats")
data class CloudNotificationStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "stage")
    val stage: String,

    @ColumnInfo(name = "bucket1", defaultValue = "0")
    val firstBucket: Int,

    @ColumnInfo(name = "bucket2", defaultValue = "0")
    val secondBucket: Int,

    @ColumnInfo(name = "bucket3", defaultValue = "0")
    val thirdBucket: Int
)
