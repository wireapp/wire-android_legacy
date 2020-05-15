package com.waz.zclient.shared.assets.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.shared.assets.AssetsApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class AssetsRemoteDataSourceTest : UnitTest() {

    @Mock
    private lateinit var assetsApi: AssetsApi

    @Mock
    private lateinit var networkHandler: NetworkHandler

    private lateinit var assetsRemoteDataSource: AssetsRemoteDataSource

    @Before
    fun setUp() {
        `when`(networkHandler.isConnected).thenReturn(true)
        assetsRemoteDataSource = AssetsRemoteDataSource(assetsApi, networkHandler)
    }

    @Test
    fun `given an assetId, when publicAsset is called, calls assetApi with given assetId`() {
        runBlocking {
            val assetId = "assetId"

            assetsRemoteDataSource.publicAsset(assetId)

            verify(assetsApi).publicAsset(assetId)
        }
    }
}
