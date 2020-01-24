package com.waz.zclient.accounts

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface AccountsRepository {
    suspend fun activeAccounts(): Either<Failure, List<ActiveAccounts>>
    suspend fun removeAccount(account: ActiveAccounts): Either<Failure, Unit>
}
