package com.waz.zclient.storage.db.notifications

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PushNotificationEventDao {
    @Query("SELECT * FROM PushNotificationEvents")
    suspend fun allPushNotificationEvents(): List<PushNotificationEventEntity>
}
