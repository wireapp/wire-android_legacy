package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.UserId
import com.waz.model.otr.UserClients.UserClientsDao
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.userclients.UserClientsEntity

class UserClientsMigrationTest extends UserDatabaseMigrationTest {
  feature("UserClients table migration") {
    scenario("UserClients migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val userId = UserId()
      val userClients = UserClients(userId, Map.empty[ClientId, Client])
      UserClientsDao.insertOrReplace(Seq(userClients))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertUserClientEntity(_, new UserClientsEntity(
          userId.str, s"""{"clients":[],"user":"${userId.str}"}"""
        ))
      })
    }
  }
}
