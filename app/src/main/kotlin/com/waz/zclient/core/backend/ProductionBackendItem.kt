import com.waz.zclient.BuildConfig
import com.waz.zclient.core.backend.Backend
import com.waz.zclient.core.backend.BackendItem

class ProductionBackendItem : BackendItem() {

    override fun environment() = Backend.PRODUCTION.environment

    override fun baseUrl() = BASE_URL

    companion object {
        private const val BASE_URL = BuildConfig.BACKEND_URL
    }
}
