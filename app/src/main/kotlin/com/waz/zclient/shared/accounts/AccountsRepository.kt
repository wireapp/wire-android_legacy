package com.waz.zclient.shared.accounts

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface AccountsRepository {
    suspend fun activeAccounts(): Either<Failure, List<ActiveAccount>>
    suspend fun deleteAccountFromDevice(account: ActiveAccount): Either<Failure, Unit>
    suspend fun deleteAccountPermanently(): Either<Failure, Unit>
}
