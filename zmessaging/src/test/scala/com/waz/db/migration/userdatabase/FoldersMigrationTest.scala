package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.FolderData.FolderDataDao
import com.waz.model.{FolderData, Name}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.folders.FoldersEntity

class FoldersMigrationTest extends UserDatabaseMigrationTest {
  feature("Folders table migration") {
    scenario("Folders migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val folderData = FolderData(name = Name(""))
      FolderDataDao.insertOrReplace(Seq(folderData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertFoldersEntity(_, new FoldersEntity(
          folderData.id.str, "", 0
        ))
      })
    }
  }
}
