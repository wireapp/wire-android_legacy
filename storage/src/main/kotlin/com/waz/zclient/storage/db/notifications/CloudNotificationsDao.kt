package com.waz.zclient.storage.db.notifications

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CloudNotificationsDao {
    @Query("SELECT * FROM FCMNotifications")
    suspend fun allCloudNotifications(): List<CloudNotificationsEntity>
}
