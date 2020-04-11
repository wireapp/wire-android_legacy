package com.waz.zclient.core.backend.items

import com.waz.zclient.core.backend.Backend
import com.waz.zclient.core.backend.BackendItem

private const val BASE_URL = "https://nginz-https.qa-demo.wire.link"
private const val WEBSOCKET_URL = "https://nginz-ssl.qa-demo.wire.link"
private const val BLACKLIST_HOST = "https://assets.qa-demo.wire.link/public/blacklist/android.json"
private const val TEAMS_URL = "https://teams.qa-demo.wire.link"
private const val ACCOUNTS_URL = "https://account.qa-demo.wire.link"
private const val WEBSITE_URL = "https://webapp.qa-demo.wire.link"

val QaBackendItem = BackendItem(
    environment = Backend.QA.environment,
    baseUrl = BASE_URL,
    websocketUrl = WEBSOCKET_URL,
    blacklistHost = BLACKLIST_HOST,
    teamsUrl = TEAMS_URL,
    accountsUrl = ACCOUNTS_URL,
    websiteUrl = WEBSITE_URL
)
