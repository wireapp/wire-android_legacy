package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.ReadReceipt.ReadReceiptDao
import com.waz.model.{MessageId, ReadReceipt, RemoteInstant, UserId}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity

class ReadReceiptMigrationTest extends UserDatabaseMigrationTest {
  feature("ReadReceipts table migration") {
    scenario("ReadReceipts migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val readReceipt = ReadReceipt(MessageId(), UserId(), RemoteInstant.Epoch)
      ReadReceiptDao.insertOrReplace(Seq(readReceipt))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertReadReceiptsEntity(_, new ReadReceiptsEntity(
          readReceipt.message.str, readReceipt.user.str, 0
        ))
      })
    }
  }
}
