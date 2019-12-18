package com.waz.zclient.core.network.api.token

import com.waz.zclient.core.di.Injector
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService

class TokenService : ApiService(Injector.networkHandler()) {

    private val tokenApi by lazy { Injector.networkClient().create(TokenApi::class.java) }

    //TODO: do we always need defaults?
    fun renewAccessToken(refreshToken: String): Either<Failure, AccessTokenResponse> = request(
        tokenApi.access(mapOf(
            "Cookie" to "zuid=$refreshToken"
        )),
        AccessTokenResponse.EMPTY
    )
}
