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
) : UseCase<Unit, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, Unit> {
        logout()
        return deleteLoggedOutAccountData()
    }

    private suspend fun logout() {
        val refreshToken = accessTokenRepository.refreshToken().token
        val accessToken = accessTokenRepository.accessToken().token
        accountsRepository.logout(refreshToken, accessToken)
    }

    private suspend fun deleteLoggedOutAccountData(): Either<Failure, Unit> =
        usersRepository.currentUserId().let {
            accountsRepository.deleteAccountFromDevice(it) //TODO should we log failure somehow?
            updateCurrentUserId(it)
        }

    private suspend fun updateCurrentUserId(loggedOutUserId: String): Either<Failure, Unit> =
        accountsRepository.activeAccounts().fold({
            clearCurrentUserId()
            Either.Right(Unit) //TODO should we log failure somehow?
        }) {
            val remainingAccountId = it.firstOrNull { it.id != loggedOutUserId }?.id
            remainingAccountId?.let { usersRepository.setCurrentUserId(it) } ?: clearCurrentUserId()
            Either.Right(Unit)
        }!!

    private fun clearCurrentUserId() = usersRepository.setCurrentUserId(String.empty())
}
