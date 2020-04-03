import com.waz.zclient.BuildConfig
import com.waz.zclient.core.backend.Backend
import com.waz.zclient.core.backend.BackendItem

val ProductionBackendItem = BackendItem(
    environment = Backend.PRODUCTION.environment,
    baseUrl = BuildConfig.BACKEND_URL,
    websocketUrl = BuildConfig.WEBSOCKET_URL,
    blacklistHost = BuildConfig.BLACKLIST_HOST,
    teamsUrl = BuildConfig.TEAMS_URL,
    accountsUrl = BuildConfig.ACCOUNTS_URL,
    websiteUrl = BuildConfig.WEBSITE_URL
)
