package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.repository.FCMNotificationStats
import com.waz.repository.FCMNotificationStatsRepository.FCMNotificationStatsDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.notifications.CloudNotificationStatsEntity

class FcmNotificationStatsMigrationTest extends UserDatabaseMigrationTest {
  feature("FCMNotificationStats table migration") {
    scenario("FCMNotificationStats migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val fcmNotStats = FCMNotificationStats("", 0, 0, 0)
      FCMNotificationStatsDao.insertOrReplace(Seq(fcmNotStats))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertCloudNotificationStatsEntity(_, new CloudNotificationStatsEntity(
          "", 0, 0, 0
        ))
      })
    }
  }
}
