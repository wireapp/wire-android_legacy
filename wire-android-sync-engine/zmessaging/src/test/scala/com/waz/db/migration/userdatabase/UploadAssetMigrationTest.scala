package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.{MD5, Mime, Sha256, UploadAssetId}
import com.waz.service.assets.UploadAssetStorage.UploadAssetDao
import com.waz.service.assets._
import com.waz.sync.client.AssetClient.Retention
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.assets.UploadAssetsEntity

class UploadAssetMigrationTest extends UserDatabaseMigrationTest {
  feature("Properties table migration") {
    scenario("Properties migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val byteArray = new Array[Byte](3)
      val uploadAsset = UploadAsset(UploadAssetId(), None, "name", Sha256.Empty,
        MD5.apply(byteArray), Mime.Unknown, PreviewNotReady, 0, 0, Retention.Persistent, public = false,
        NoEncryption, None, DetailsNotReady, UploadAssetStatus.NotStarted, None)
      UploadAssetDao.insertOrReplace(Seq(uploadAsset))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertUploadAssetEntity(_, new UploadAssetsEntity(
          uploadAsset.id.str,
          "null",
          "name",
          null,
          byteArray,
          "",
          "not_ready",
          0,
          0,
          5,
          false,
          "",
          null,
          """{"DetailsNotReady":{}}""",
          1,
          null
        ))
      })
    }
  }
}
