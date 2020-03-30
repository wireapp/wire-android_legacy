import com.waz.zclient.BuildConfig
import com.waz.zclient.core.backend.items.Backend
import com.waz.zclient.core.backend.items.BackendItem

class ProductionBackendItem : BackendItem() {

    override fun environment() = Backend.PRODUCTION.environment

    override fun baseUrl() = BASE_URL

    companion object {
        private const val BASE_URL = BuildConfig.BACKEND_URL
    }
}
