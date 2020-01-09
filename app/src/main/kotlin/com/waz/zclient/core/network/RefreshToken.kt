package com.waz.zclient.core.network

import com.google.gson.annotations.SerializedName
import org.threeten.bp.Instant //TODO: should we continue using this library?

data class RefreshToken(
    val token: String,
    val expiryDate: Instant?
)

inline class RefreshTokenResponse(val tokenText: String)

data class RefreshTokenPreference(
    @SerializedName("token")
    val token: String,
    @SerializedName("expiryDate")
    val expiryDate: Long
)

class RefreshTokenMapper {
    fun from(response: RefreshTokenResponse) = response.tokenText.let {
        RefreshToken(it, calculateExpiryDate(it))
    }

    private fun calculateExpiryDate(tokenText: String): Instant? {
        val parts = tokenText.split('.')
        val datePart = parts.find { it.contains("d=") }?.drop(2)
        return datePart?.toLong()?.let { Instant.ofEpochSecond(it) }
    }

    fun toResponse(refreshToken: RefreshToken) = RefreshTokenResponse(refreshToken.token)

    fun from(pref: RefreshTokenPreference) = RefreshToken(pref.token, Instant.ofEpochSecond(pref.expiryDate))

    fun toPreference(refreshToken: RefreshToken) = RefreshTokenPreference(
        token = refreshToken.token,
        expiryDate = refreshToken.expiryDate!!.epochSecond //TODO check nullities
    )

    fun responseToPref(response: RefreshTokenResponse): RefreshTokenPreference = toPreference(from(response))
}
