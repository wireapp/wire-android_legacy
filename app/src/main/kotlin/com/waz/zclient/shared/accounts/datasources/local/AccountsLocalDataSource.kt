package com.waz.zclient.shared.accounts.datasources.local

import com.waz.zclient.core.exception.DatabaseFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.accountdata.ActiveAccountsDao
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity

class AccountsLocalDataSource(private val activeAccountsDao: ActiveAccountsDao) {

    suspend fun activeAccounts() =
        requestDatabase {
            activeAccountsDao.activeAccounts()
        }

    suspend fun activeAccountById(accountId: String): Either<DatabaseFailure, ActiveAccountsEntity?> =
        requestDatabase {
            activeAccountsDao.activeAccountById(accountId)
        }

    suspend fun removeAccount(accountId: String) =
        requestDatabase {
            activeAccountsDao.removeAccount(accountId)
        }
}
