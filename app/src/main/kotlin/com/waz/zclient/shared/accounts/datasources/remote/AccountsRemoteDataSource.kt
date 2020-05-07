package com.waz.zclient.shared.accounts.datasources.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.core.network.api.token.TokenService

class AccountsRemoteDataSource(
    private val tokenService: TokenService,
    override val networkHandler: NetworkHandler
) : ApiService() {

    suspend fun logout(refreshToken: String, accessToken: String): Either<Failure, Unit> =
        tokenService.logout(refreshToken, accessToken)
}
