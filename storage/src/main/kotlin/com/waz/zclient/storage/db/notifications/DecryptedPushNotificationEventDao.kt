package com.waz.zclient.storage.db.notifications

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DecryptedPushNotificationEventDao {
    @Query("SELECT * FROM DecryptedPushNotificationEvents")
    suspend fun allDecryptedPushNotificationEvents(): List<DecryptedPushNotificationEventEntity>
}
