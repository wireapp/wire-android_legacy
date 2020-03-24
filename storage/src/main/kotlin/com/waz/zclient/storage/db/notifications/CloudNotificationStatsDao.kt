package com.waz.zclient.storage.db.notifications

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CloudNotificationStatsDao {
    @Query("SELECT * FROM FCMNotificationStats")
    suspend fun allCloudNotificationStats(): List<CloudNotificationStatsEntity>
}
