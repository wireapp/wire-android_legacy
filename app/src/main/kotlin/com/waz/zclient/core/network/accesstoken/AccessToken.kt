package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.network.api.token.AccessTokenResponse
import com.waz.zclient.storage.db.accountdata.AccessTokenEntity

data class AccessToken(
    val token: String,
    val tokenType: String,
    val expiresIn: String
) {
    companion object {
        val EMPTY = AccessToken(String.empty(), String.empty(), String.empty())
    }
}

class AccessTokenMapper {
    fun from(entity: AccessTokenEntity) = AccessToken(
        token = entity.token,
        tokenType = entity.tokenType,
        expiresIn = entity.expiresInMillis.toString()
    )

    fun from(response: AccessTokenResponse) = AccessToken(
        token = response.token,
        tokenType = response.type,
        expiresIn = response.expiresIn
    )

    fun toEntity(accessToken: AccessToken) = AccessTokenEntity(
        token = accessToken.token,
        tokenType = accessToken.tokenType,
        expiresInMillis = accessToken.expiresIn.toLong()
    )
}
