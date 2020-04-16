package com.waz.db.migration

import com.waz.KotlinMigrationHelper
import com.waz.model.{ConvId, MessageContentIndexDao, MessageContentIndexEntry, MessageId, RemoteInstant}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.messages.MessageContentIndexEntity

class MessageContentIndexMigrationTest extends MigrationTest {
  feature("MessageContentIndex table migration") {
    scenario("MessageContentIndex migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val entry = MessageContentIndexEntry(MessageId(), ConvId(), "", RemoteInstant.Epoch)
      MessageContentIndexDao.insertOrReplace(Seq(entry))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertMessageContentIndexEntity(_, new MessageContentIndexEntity(
          entry.messageId.str, entry.convId.str, "", 0
        ))
      })
    }
  }
}
