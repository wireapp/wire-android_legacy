package com.waz.zclient.shared.accounts.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.runBlocking

object CannotFindActiveAccount : ActiveAccountsErrors()
sealed class ActiveAccountsErrors : FeatureFailure()

class GetActiveAccountUseCase(
    private val accountsRepository: AccountsRepository,
    private val userRepository: UsersRepository
) : UseCase<ActiveAccount, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, ActiveAccount> =
        userRepository.currentUserId().let {
            if (it.isEmpty()) Either.Left(CannotFindActiveAccount)
            else activeAccountById(it)
        }

    private fun activeAccountById(id: String): Either<Failure, ActiveAccount> = runBlocking {
        accountsRepository.activeAccountById(id).flatMap { account ->
            account?.let { Either.Right(it) }
                ?: Either.Left(CannotFindActiveAccount)
        }
    }
}
