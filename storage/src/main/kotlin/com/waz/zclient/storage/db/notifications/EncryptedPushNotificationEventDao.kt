package com.waz.zclient.storage.db.notifications

import androidx.room.Dao
import androidx.room.Query

@Dao
interface EncryptedPushNotificationEventDao {
    @Query("SELECT * FROM EncryptedPushNotificationEvents")
    suspend fun allEncryptedPushNotificationEvents(): List<EncryptedPushNotificationEventEntity>
}
