package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.Liking.LikingDao
import com.waz.model.{Liking, MessageId, RemoteInstant, UserId}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.messages.LikesEntity

class LikingsMigrationTest extends UserDatabaseMigrationTest {
  feature("Likings table migration") {
    scenario("Likings migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val liking = Liking(MessageId(), UserId(), RemoteInstant.Epoch, Liking.Action.Like)
      LikingDao.insertOrReplace(Seq(liking))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertLikesEntity(_, new LikesEntity(
          liking.message.str, liking.user.str, 0, Liking.Action.Like.serial
        ))
      })
    }
  }
}
