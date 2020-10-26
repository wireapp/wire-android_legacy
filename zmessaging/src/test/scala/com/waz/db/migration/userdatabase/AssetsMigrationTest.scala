package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.AssetData
import com.waz.model.AssetData.AssetDataDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.assets.AssetsV1Entity

class AssetsMigrationTest extends UserDatabaseMigrationTest {

  feature("Assets table migration") {
    scenario("Assets migration with default values"){
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val assetData = AssetData()
      AssetDataDao.insertOrReplace(Seq(assetData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertAssetV1Entity(_, new AssetsV1Entity(
          assetData.id.str, "Empty",
          s"""{"sizeInBytes":0,"mime":"","id":"${assetData.id.str}","status":{"status":"NotStarted"}}"""
        ))
      })
    }
  }
}
