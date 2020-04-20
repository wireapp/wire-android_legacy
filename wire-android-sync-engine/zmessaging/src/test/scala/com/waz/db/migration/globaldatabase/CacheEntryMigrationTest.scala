package com.waz.db.migration.globaldatabase

import com.waz.cache.CacheEntryData
import com.waz.cache.CacheEntryData.CacheEntryDao
import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.CacheKey
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.cache.CacheEntryEntity

class CacheEntryMigrationTest extends GlobalDatabaseMigrationTest {
  feature("CacheEntry table migration") {
    scenario("CacheEntry migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zGlobalDb.getWritableDatabase)
      val cacheEntry = CacheEntryData(CacheKey())
      CacheEntryDao.insertOrReplace(Seq(cacheEntry))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertCacheEntryEntity(_, new CacheEntryEntity(
          cacheEntry.key.str,
          cacheEntry.fileId.str,
          null,
          cacheEntry.lastUsed,
          cacheEntry.timeout,
          null,
          null,
          "",
          null,
          null
        ))
      })
    }
  }
}
