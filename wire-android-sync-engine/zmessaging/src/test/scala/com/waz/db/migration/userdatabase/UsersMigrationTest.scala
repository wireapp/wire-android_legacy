package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.UserData
import com.waz.model.UserData.UserDataDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.users.model.UserEntity

class UsersMigrationTest extends UserDatabaseMigrationTest {

  feature("Users table migration") {
    scenario("Users migration with default values"){
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val userName = "userName"
      val userData = UserData(name = userName)
      UserDataDao.insertOrReplace(Seq(userData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertUserEntity(_, new UserEntity(
          userData.id.str,
          null,
          userName,
          null,
          null,
          null,
          null,
          0,
          userData.searchKey.asciiRepresentation,
          "unconnected",
          0,
          null,
          null,
          "Other",
          null,
          "UNKNOWN",
          false,
          0,
          null,
          null,
          null,
          null,
          null,
          0,
          0,
          null
        ))
      })
    }
  }
}
