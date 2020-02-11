package com.waz.zclient.accounts.domain.usecase

import com.waz.zclient.accounts.AccountsRepository
import com.waz.zclient.accounts.domain.model.ActiveAccount
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.storage.pref.GlobalPreferences

class GetActiveAccountUseCase(
    private val accountsRepository: AccountsRepository,
    private val globalPreferences: GlobalPreferences
) : UseCase<ActiveAccount, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, ActiveAccount> =
        accountsRepository.activeAccounts().map { activeAccounts ->
            activeAccounts.first { it.id == globalPreferences.activeUserId }
        }
}
