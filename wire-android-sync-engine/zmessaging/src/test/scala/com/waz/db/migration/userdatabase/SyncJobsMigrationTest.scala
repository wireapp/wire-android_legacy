package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.SyncId
import com.waz.model.sync.SyncJob.SyncJobDao
import com.waz.model.sync.{SyncJob, SyncRequest}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.sync.SyncJobsEntity

class SyncJobsMigrationTest extends UserDatabaseMigrationTest {
  feature("SyncJobs table migration") {
    scenario("SyncJobs migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val syncJob = SyncJob(SyncId(), SyncRequest.SyncSelf)
      SyncJobDao.insertOrReplace(Seq(syncJob))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertSyncJobsEntity(_, new SyncJobsEntity(
          syncJob.id.str, SyncJob.Encoder.apply(syncJob).toString
        ))
      })
    }
  }
}
