package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.ConversationFolderData.ConversationFolderDataDao
import com.waz.model.{ConvId, ConversationFolderData, FolderId}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity

class ConversationFolderMigrationTest extends UserDatabaseMigrationTest {
  feature("ConversationFolders table migration") {
    scenario("ConversationFolders migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val convFolderData = ConversationFolderData(ConvId(), FolderId())
      ConversationFolderDataDao.insertOrReplace(Seq(convFolderData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertConvFoldersEntity(_, new ConversationFoldersEntity(
          convFolderData.convId.str, convFolderData.folderId.str
        ))
      })
    }
  }
}
