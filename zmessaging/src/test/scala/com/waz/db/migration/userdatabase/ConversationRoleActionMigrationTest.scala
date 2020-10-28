package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.ConversationRoleAction.ConversationRoleActionDao
import com.waz.model.{ConvId, ConversationRoleAction}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity

class ConversationRoleActionMigrationTest extends UserDatabaseMigrationTest {
  feature("ConversationRoleAction table migration") {
    scenario("ConversationRoleAction migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val convRoleAction = ConversationRoleAction("", "", ConvId())
      ConversationRoleActionDao.insertOrReplace(Seq(convRoleAction))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertConvRoleActionEntity(_, new ConversationRoleActionEntity(
          "", "", convRoleAction.convId.str
        ))
      })
    }
  }
}
