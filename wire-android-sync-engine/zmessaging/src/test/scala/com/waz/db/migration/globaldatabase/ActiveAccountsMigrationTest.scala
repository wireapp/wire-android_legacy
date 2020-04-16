package com.waz.db.migration.globaldatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.AccountData.AccountDataDao
import com.waz.model.AccountData
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity

class ActiveAccountsMigrationTest extends GlobalDatabaseMigrationTest {
  feature("ActiveAccounts table migration") {
    scenario("ActiveAccounts migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zGlobalDb.getWritableDatabase)
      val accountData = AccountData()
      AccountDataDao.insertOrReplace(Seq(accountData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertActiveAccountsEntity(_, new ActiveAccountsEntity(
          accountData.id.str, null, "", null, null, null
        ))
      })
    }
  }
}
