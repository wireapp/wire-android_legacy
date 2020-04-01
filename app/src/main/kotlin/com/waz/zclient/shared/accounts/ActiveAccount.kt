package com.waz.zclient.shared.accounts

import com.waz.zclient.core.network.accesstoken.AccessToken
import com.waz.zclient.core.network.accesstoken.AccessTokenMapper
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity
import com.waz.zclient.storage.db.accountdata.SsoIdEntity

data class ActiveAccount(
    val id: String,
    val teamId: String?,
    val accessToken: AccessToken?,
    val refreshToken: String,
    val pushToken: String?,
    val ssoId: SsoId?
)

data class SsoId(
    val subject: String,
    val tenant: String
)

class AccountMapper {

    private val accessTokenMapper = AccessTokenMapper()

    fun toEntity(activeAccount: ActiveAccount) = ActiveAccountsEntity(
        id = activeAccount.id,
        teamId = activeAccount.teamId,
        accessToken = activeAccount.accessToken?.let { accessTokenMapper.toEntity(it) },
        refreshToken = activeAccount.refreshToken,
        pushToken = activeAccount.pushToken,
        ssoId = activeAccount.ssoId?.let { toEntity(it) })

    private fun toEntity(ssoId: SsoId) = SsoIdEntity(
        subject = ssoId.subject,
        tenant = ssoId.tenant
    )

    fun from(activeAccountEntity: ActiveAccountsEntity) = ActiveAccount(
        id = activeAccountEntity.id,
        teamId = activeAccountEntity.teamId,
        accessToken = activeAccountEntity.accessToken?.let { accessTokenMapper.from(it) },
        refreshToken = activeAccountEntity.refreshToken,
        pushToken = activeAccountEntity.pushToken,
        ssoId = activeAccountEntity.ssoId?.let { from(it) }
    )

    private fun from(ssoId: SsoIdEntity) = SsoId(
        subject = ssoId.subject,
        tenant = ssoId.tenant
    )
}
