package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.MsgDeletion.MsgDeletionDao
import com.waz.model.{MessageId, MsgDeletion}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.messages.MessageDeletionEntity
import org.threeten.bp.Instant

class MessageDeletionMigrationTest extends UserDatabaseMigrationTest {
  feature("MessageDeletion table migration") {
    scenario("MessageDeletion migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val epochMilli = 123L
      val messageDeletion = MsgDeletion(MessageId(), Instant.ofEpochMilli(epochMilli))
      MsgDeletionDao.insertOrReplace(Seq(messageDeletion))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertMessageDeletionEntity(_, new MessageDeletionEntity(
          messageDeletion.msg.str, epochMilli.toInt
        ))
      })
    }
  }
}
