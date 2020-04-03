package com.waz.zclient.shared.accounts.datasources.local

import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.accountdata.ActiveAccountsDao
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity

class AccountsLocalDataSource(private val activeAccountsDao: ActiveAccountsDao) {

    suspend fun activeAccounts() =
        requestDatabase {
            activeAccountsDao.activeAccounts()
        }

    suspend fun removeAccount(account: ActiveAccountsEntity) =
        requestDatabase {
            activeAccountsDao.removeAccount(account)
        }
}
