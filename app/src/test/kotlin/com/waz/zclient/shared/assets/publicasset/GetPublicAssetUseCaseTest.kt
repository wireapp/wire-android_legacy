package com.waz.zclient.shared.assets.publicasset

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.assets.AssetsRepository
import com.waz.zclient.shared.assets.usecase.GetPublicAssetUseCase
import com.waz.zclient.shared.assets.usecase.PublicAsset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class GetPublicAssetUseCaseTest : UnitTest() {

    @Mock
    private lateinit var assetsRepository: AssetsRepository

    @Mock
    private lateinit var publicAsset: PublicAsset

    private lateinit var publicAssetLoader: GetPublicAssetUseCase

    @Before
    fun setUp() {
        publicAssetLoader = GetPublicAssetUseCase(assetsRepository)
    }

    @Test
    fun `given a PublicAsset, when run is called, fetches publicAsset from assetsRepository with asset's id`() =
        runBlockingTest {
            val assetId = "assetId"
            `when`(publicAsset.assetId).thenReturn(assetId)

            publicAssetLoader.run(publicAsset)

            verify(assetsRepository).publicAsset(assetId)
        }
}
