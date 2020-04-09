package com.waz.zclient.core.backend.items

import com.waz.zclient.core.backend.Backend
import com.waz.zclient.core.backend.BackendItem

private const val BASE_URL = "https://staging-nginz-https.zinfra.io"
private const val WEBSOCKET_URL = "https://staging-nginz-ssl.zinfra.io/await"
private const val BLACKLIST_HOST = "https://clientblacklist.wire.com/staging/android"
private const val TEAMS_URL = "https://wire-teams-staging.zinfra.io"
private const val ACCOUNTS_URL = "https://wire-account-staging.zinfra.io"
private const val WEBSITE_URL = "https://wire.com"

val StagingBackendItem = BackendItem(
    environment = Backend.STAGING.environment,
    baseUrl = BASE_URL,
    websocketUrl = WEBSOCKET_URL,
    blacklistHost = BLACKLIST_HOST,
    teamsUrl = TEAMS_URL,
    accountsUrl = ACCOUNTS_URL,
    websiteUrl = WEBSITE_URL
)
