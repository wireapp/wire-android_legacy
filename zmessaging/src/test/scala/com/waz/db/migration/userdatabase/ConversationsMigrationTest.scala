package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.ConversationData
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.conversations.ConversationsEntity

class ConversationsMigrationTest extends UserDatabaseMigrationTest {

  feature("Conversation table migration") {
    scenario("Conversation migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val conversationData = ConversationData()
      ConversationDataDao.insertOrReplace(Seq(conversationData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertConversationsEntity(_, new ConversationsEntity(
          conversationData.id.str,
          conversationData.remoteId.str,
          null,
          conversationData.creator.str,
          conversationData.convType.id,
          null,
          null,
          0,
          conversationData.isActive,
          0,
          conversationData.muted.toInt,
          0,
          conversationData.archived,
          0,
          null,
          conversationData.generatedName.str,
          null,
          conversationData.unreadCount.normal,
          conversationData.failedCount,
          conversationData.hidden,
          null,
          null,
          conversationData.verified.name(),
          null,
          null,
          conversationData.unreadCount.call,
          conversationData.unreadCount.ping,
          null,
          null,
          null,
          conversationData.unreadCount.mentions,
          conversationData.unreadCount.quotes,
          null
        ))
      })
    }
  }

}
