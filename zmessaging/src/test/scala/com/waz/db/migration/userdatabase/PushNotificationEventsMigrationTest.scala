package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.PushNotificationEvents.PushNotificationEventsDao
import com.waz.model.{PushNotificationEvent, Uid}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.notifications.PushNotificationEventEntity
import org.json.JSONObject

class PushNotificationEventsMigrationTest extends UserDatabaseMigrationTest {
  feature("PushNotificationEvents table migration") {
    scenario("PushNotificationEvents migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val pushNotificationEvent = PushNotificationEvent(Uid(), 1, event = new JSONObject(), transient = false)
      PushNotificationEventsDao.insertOrReplace(Seq(pushNotificationEvent))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertPushNotEventEntity(_, new PushNotificationEventEntity(
          pushNotificationEvent.pushId.str, 1,  false, "{}", null, false
        ))
      })
    }
  }
}
