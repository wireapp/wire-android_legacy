package com.waz.zclient.storage.db.notifications

import androidx.room.Dao
import androidx.room.Query

@Dao
interface NotificationDataDao {
    @Query("SELECT * FROM NotificationData")
    suspend fun allNotificationsData(): List<NotificationDataEntity>
}
