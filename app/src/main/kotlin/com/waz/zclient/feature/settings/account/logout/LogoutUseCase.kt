package com.waz.zclient.feature.settings.account.logout

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.accesstoken.AccessTokenRepository
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.shared.user.UsersRepository

class LogoutUseCase(
    private val accountsRepository: AccountsRepository,
    private val accessTokenRepository: AccessTokenRepository,
    private val usersRepository: UsersRepository
) : UseCase<LogoutStatus, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, LogoutStatus> {
        logout() //TODO check w/ backend team what is the side effect if this call fails
        return deleteLoggedOutAccountData()
    }

    private suspend fun logout() {
        val refreshToken = accessTokenRepository.refreshToken().token
        val accessToken = accessTokenRepository.accessToken().token
        accountsRepository.logout(refreshToken, accessToken)
    }

    private suspend fun deleteLoggedOutAccountData(): Either<Failure, LogoutStatus> =
        usersRepository.currentUserId().let {
            accountsRepository.deleteAccountFromDevice(it) //TODO should we log failure somehow?
            updateCurrentUserId(it)
        }

    private suspend fun updateCurrentUserId(loggedOutUserId: String): Either<Failure, LogoutStatus> =
        accountsRepository.activeAccounts().fold({
            clearCurrentUserId()
            Either.Right(CouldNotReadRemainingAccounts)
        }) {
            val remainingAccountId = it.firstOrNull { it.id != loggedOutUserId }?.id
            val status = if (remainingAccountId == null) {
                clearCurrentUserId()
                NoAccountsLeft
            } else {
                usersRepository.setCurrentUserId(remainingAccountId)
                AnotherAccountExists
            }
            Either.Right(status)
        }!!

    private fun clearCurrentUserId() = usersRepository.setCurrentUserId(String.empty())
}

sealed class LogoutStatus
object NoAccountsLeft : LogoutStatus()
object AnotherAccountExists : LogoutStatus()
object CouldNotReadRemainingAccounts : LogoutStatus()
