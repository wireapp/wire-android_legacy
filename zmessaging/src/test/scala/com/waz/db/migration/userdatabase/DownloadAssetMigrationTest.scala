package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.{DownloadAssetId, Mime}
import com.waz.service.assets.DownloadAssetStorage.DownloadAssetDao
import com.waz.service.assets.{BlobDetails, DownloadAsset, DownloadAssetStatus}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.assets.DownloadAssetsEntity

class DownloadAssetMigrationTest extends UserDatabaseMigrationTest {
  feature("DownloadAssets table migration") {
    scenario("DownloadAssets migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val downloadAsset = DownloadAsset(DownloadAssetId(), Mime.Unknown, "", None, BlobDetails, 0, 0, DownloadAssetStatus.NotStarted)
      DownloadAssetDao.insertOrReplace(Seq(downloadAsset))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertDownloadAssetsEntity(_, new DownloadAssetsEntity(
          s"""{"str":"${downloadAsset.id.str}"}""",
          "", 0, 0, "", "null",
          """{"BlobDetails":{}}""",
          1
        ))
      })
    }
  }
}
