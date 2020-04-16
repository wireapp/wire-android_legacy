package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.{AssetId, Mime, Sha256}
import com.waz.service.assets.AssetStorageImpl.AssetDao
import com.waz.service.assets.{Asset, BlobDetails, NoEncryption}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.assets.AssetsEntity

class Assets2MigrationTest extends UserDatabaseMigrationTest {
  feature("Asset2 table migration") {
    scenario("Asset2 migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val sha = Sha256.Empty
      val asset = Asset(AssetId(), None, sha, Mime.Unknown, NoEncryption, None, None, "", 0, BlobDetails, None)
      AssetDao.insertOrReplace(Seq(asset))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertAssetsEntity(_, new AssetsEntity(
          asset.id.str,
          null,
          "",
          "",
          Mime.Unknown.str,
          null,
          0,
          null,
          null,
          """{"BlobDetails":{}}""",
          null
        ))
      })
    }
  }
}
