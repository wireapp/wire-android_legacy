package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "FCMNotificationStats")
data class CloudNotificationStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "stage")
    val stage: String,

    @ColumnInfo(name = "bucket1")
    val firstBucket: Int,

    @ColumnInfo(name = "bucket2")
    val secondBucket: Int,

    @ColumnInfo(name = "bucket3")
    val thirdBucket: Int
)
