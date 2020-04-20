package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.{FCMNotification, Uid}
import com.waz.repository.FCMNotificationsRepository.FCMNotificationsDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.notifications.CloudNotificationsEntity
import org.threeten.bp.Instant

class FcmNotificationsMigration extends UserDatabaseMigrationTest {
  feature("FCMNotifications table migration") {
    scenario("FCMNotifications migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val fcmNot = FCMNotification(Uid(), "", Instant.EPOCH)
      FCMNotificationsDao.insertOrReplace(Seq(fcmNot))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertCloudNotificationsEntity(_, new CloudNotificationsEntity(
          fcmNot.id.str, "", 0
        ))
      })
    }
  }
}
