package com.waz.zclient.shared.accounts.datasources

import com.waz.zclient.shared.accounts.AccountMapper
import com.waz.zclient.shared.accounts.datasources.local.AccountsLocalDataSource
import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.shared.user.datasources.remote.UsersRemoteDataSource

class AccountsDataSource(
    private val accountMapper: AccountMapper,
    private val usersRemoteDataSource: UsersRemoteDataSource,
    private val accountsLocalDataSource: AccountsLocalDataSource
) : AccountsRepository {

    override suspend fun activeAccounts() = accountsLocalDataSource.activeAccounts()
        .map { entityList -> entityList.map { accountMapper.from(it) } }

    override suspend fun deleteAccountFromDevice(account: ActiveAccount) =
        accountsLocalDataSource.removeAccount(accountMapper.toEntity(account))

    override suspend fun deleteAccountPermanently(): Either<Failure, Unit> =
        usersRemoteDataSource.deleteAccountPermanently()
}
