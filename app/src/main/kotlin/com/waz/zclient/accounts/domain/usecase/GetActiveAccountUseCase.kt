package com.waz.zclient.accounts.domain.usecase

import com.waz.zclient.accounts.AccountsRepository
import com.waz.zclient.accounts.domain.model.ActiveAccount
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.getOrElse
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository
import kotlinx.coroutines.runBlocking

object CannotFindActiveAccount : ActiveAccountsErrors()
sealed class ActiveAccountsErrors : FeatureFailure()

class GetActiveAccountUseCase(
    private val accountsRepository: AccountsRepository,
    private val userRepository: UsersRepository
) : UseCase<ActiveAccount, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, ActiveAccount> =
        try {
            accountsRepository.activeAccounts().map { activeAccounts ->
                activeAccounts.first { filterActiveId(it) }
            }
        } catch (exception: NoSuchElementException) {
            Either.Left(CannotFindActiveAccount)
        }

    private fun filterActiveId(account: ActiveAccount): Boolean = runBlocking {
        account.id == userRepository.currentUserId().getOrElse(String.empty())
    }
}
