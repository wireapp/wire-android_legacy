package com.waz.zclient.assets

import com.waz.zclient.UnitTest
import com.waz.zclient.assets.datasources.AssetsRemoteDataSource
import com.waz.zclient.assets.mapper.AssetMapper
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@ExperimentalCoroutinesApi
class AssetsRepositoryTest : UnitTest() {

    @Mock
    private lateinit var assetsRemoteDataSource: AssetsRemoteDataSource

    @Mock
    private lateinit var assetMapper: AssetMapper

    private lateinit var assetsRepository: AssetsRepository

    @Before
    fun setUp() {
        assetsRepository = AssetsRepository(assetsRemoteDataSource, assetMapper)
    }

    @Test
    fun `given an asset id, when publicAsset is called, fetches response from remoteDataSource`() = runBlockingTest {
        val assetId = "assetId"
        `when`(assetsRemoteDataSource.publicAsset(assetId)).thenReturn(Either.Left(ServerError))

        assetsRepository.publicAsset(assetId)

        verify(assetsRemoteDataSource).publicAsset(assetId)
    }

    @Test
    fun `given remoteDataSource publicAsset call is successful, when publicAsset is called, maps the response`() =
        runBlockingTest {
            val assetId = "assetId"
            val responseBody = mock(ResponseBody::class.java)

            `when`(assetsRemoteDataSource.publicAsset(assetId)).thenReturn(Either.Right(responseBody))

            assetsRepository.publicAsset(assetId)

            verify(assetMapper).toInputStream(responseBody)
        }

    @Test
    fun `given remoteDataSource publicAsset call fails, when publicAsset is called, does not map the response`() =
        runBlockingTest {
            val assetId = "assetId"
            `when`(assetsRemoteDataSource.publicAsset(assetId)).thenReturn(Either.Left(ServerError))

            assetsRepository.publicAsset(assetId)

            verifyNoInteractions(assetMapper)
        }
}
