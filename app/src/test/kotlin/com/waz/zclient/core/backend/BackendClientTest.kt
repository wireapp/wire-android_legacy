package com.waz.zclient.core.backend

import ProductionBackendItem
import com.waz.zclient.UnitTest
import com.waz.zclient.core.backend.items.QaBackendItem
import com.waz.zclient.core.backend.items.StagingBackendItem
import com.waz.zclient.core.extension.empty
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test

class BackendClientTest : UnitTest() {

    private lateinit var backendClient: BackendClient

    @Before
    fun setUp() {
        backendClient = BackendClient()
    }

    @Test
    fun `given staging preferences environment value is injected, return StagingBackendItem()`() {
        backendClient.get(Backend.STAGING.environment) shouldBe StagingBackendItem
    }

    @Test
    fun `given QA preferences environment value is injected, return QaBackendItem()`() {
        backendClient.get(Backend.QA.environment) shouldBe QaBackendItem
    }

    @Test
    fun `given Production preferences environment values is injected, return ProductionBackendItem()`() {
        backendClient.get(Backend.PRODUCTION.environment) shouldBe ProductionBackendItem
    }

    @Test
    fun `given non environment value is injected, return ProductionBackendItem()`() {
        backendClient.get(String.empty()) shouldBe ProductionBackendItem
    }
}
