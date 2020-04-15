package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.MessageData
import com.waz.model.MessageData.MessageDataDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.messages.MessagesEntity

class MessagesMigrationTest extends UserDatabaseMigrationTest {
  feature("Messages table migration") {
    scenario("Messages migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val messageData = MessageData()
      MessageDataDao.insertOrReplace(Seq(messageData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertMessageEntity(_, new MessagesEntity(
          messageData.id.str,
          messageData.convId.str,
          "Text",
          messageData.userId.str,
          null,
          null,
          messageData.time.toEpochMilli.toInt,
          false,
          null,
          null,
          null,
          null,
          "SENT",
          0,
          0,
          0,
          null,
          null,
          false,
          null,
          null,
          0,
          null,
          null
        ))
      })
    }
  }
}
