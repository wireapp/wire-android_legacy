package com.waz.zclient.shared.accounts.usecase

import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.getOrElse
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.runBlocking

object CannotFindActiveAccount : ActiveAccountsErrors()
sealed class ActiveAccountsErrors : FeatureFailure()

class GetActiveAccountUseCase(
    private val accountsRepository: AccountsRepository,
    private val userRepository: UsersRepository
) : UseCase<ActiveAccount, Unit>() {

    override suspend fun run(params: Unit) =
        accountsRepository.activeAccounts().flatMap { activeAccounts ->
            activeAccounts.firstOrNull {
                filterActiveId(it)
            }?.let { Either.Right(it) } ?: Either.Left(CannotFindActiveAccount)
        }

    private fun filterActiveId(account: ActiveAccount): Boolean = runBlocking {
        account.id == userRepository.currentUserId().getOrElse(String.empty())
    }
}
