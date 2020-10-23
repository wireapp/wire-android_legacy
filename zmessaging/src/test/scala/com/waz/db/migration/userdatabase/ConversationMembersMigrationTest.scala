package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.ConversationMemberData.ConversationMemberDataDao
import com.waz.model.{ConvId, ConversationMemberData, ConversationRole, UserId}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity

class ConversationMembersMigrationTest extends UserDatabaseMigrationTest {
  feature("ConversationMembers table migration") {
    scenario("ConversationMembers migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val conversationMember = ConversationMemberData(UserId(), ConvId(), ConversationRole.AdminRole)
      ConversationMemberDataDao.insertOrReplace(Seq(conversationMember))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertConvMembersEntity(_, new ConversationMembersEntity(
          conversationMember.userId.str,
          conversationMember.convId.str,
          "wire_admin"
        ))
      })
    }
  }
}
