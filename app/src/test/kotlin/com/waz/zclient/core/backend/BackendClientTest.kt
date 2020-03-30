package com.waz.zclient.core.backend

import ProductionBackendItem
import com.waz.zclient.UnitTest
import com.waz.zclient.core.backend.items.Backend
import com.waz.zclient.core.backend.items.BackendClient
import com.waz.zclient.core.backend.items.QaBackendItem
import com.waz.zclient.core.backend.items.StagingBackendItem
import com.waz.zclient.core.extension.empty
import org.junit.Test

class BackendClientTest : UnitTest() {

    private val backendClient = BackendClient()

    @Test
    fun `given staging preferences environment value is injected, return StagingBackendItem()`() {
        assert(backendClient.get(Backend.STAGING.environment) is StagingBackendItem)
    }

    @Test
    fun `given QA preferences environment value is injected, return QaBackendItem()`() {
        assert(backendClient.get(Backend.QA.environment) is QaBackendItem)
    }

    @Test
    fun `given Production preferences environment values is injected, return ProductionBackendItem()`() {
        assert(backendClient.get(Backend.PRODUCTION.environment) is ProductionBackendItem)
    }

    @Test
    fun `given non environment value is injected, return ProductionBackendItem()`() {
        assert(backendClient.get(String.empty()) is ProductionBackendItem)
    }
}
