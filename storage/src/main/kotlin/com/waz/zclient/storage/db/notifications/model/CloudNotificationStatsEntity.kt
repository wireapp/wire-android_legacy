package com.waz.zclient.storage.db.notifications.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "FCMNotificationStats")
data class CloudNotificationStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "stage")
    val stage: String,

    @ColumnInfo(name = "bucket1")
    var firstBucket: Int?,

    @ColumnInfo(name = "bucket2")
    var secondBucket: Int?,

    @ColumnInfo(name = "bucket3")
    var thirdBucket: Int?
)
