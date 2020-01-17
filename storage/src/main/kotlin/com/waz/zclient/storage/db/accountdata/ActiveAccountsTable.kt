package com.waz.zclient.storage.db.accountdata

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ActiveAccounts")
data class ActiveAccountsTable(
    @ColumnInfo(name = "_id")
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "team_id")
    val teamId: String?,

    @ColumnInfo(name = "cookie")
    val refreshToken: String,

    @ColumnInfo(name = "access_token")
    val accessToken: AccessTokenEntity?,

    @ColumnInfo(name = "registered_push")
    val pushToken: String?,

    @ColumnInfo(name = "sso_id")
    val ssoId: SsoIdEntity?
)
