package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.EditHistory.EditHistoryDao
import com.waz.model.{EditHistory, MessageId, RemoteInstant}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.history.EditHistoryEntity

class EditHistoryMigrationTest extends UserDatabaseMigrationTest {
  feature("EditHistory table migration") {
    scenario("EditHistory migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val editHistory = EditHistory(MessageId(), MessageId(), RemoteInstant.Epoch)
      EditHistoryDao.insertOrReplace(Seq(editHistory))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertEditHistoryEntity(_, new EditHistoryEntity(
          editHistory.originalId.str, editHistory.updatedId.str, 0
        ))
      })
    }
  }
}
