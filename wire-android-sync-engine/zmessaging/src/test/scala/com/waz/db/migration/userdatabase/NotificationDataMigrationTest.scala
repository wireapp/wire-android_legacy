package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.NotificationData
import com.waz.model.NotificationData.NotificationDataDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.notifications.NotificationDataEntity

class NotificationDataMigrationTest extends UserDatabaseMigrationTest {
  feature("NotificationData table migration") {
    scenario("NotificationData migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val notificationData = NotificationData()
      NotificationDataDao.insertOrReplace(notificationData)
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertNotificationDataEntity(_, new NotificationDataEntity(
          notificationData.id.str,
          NotificationData.Encoder.apply(notificationData).toString
        ))
      })
    }
  }
}
