package com.waz.zclient.core.images

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.io.InputStream

@ExperimentalCoroutinesApi
class InputStreamFetcherTest : UnitTest() {

    @Mock
    private lateinit var callback: DataFetcher.DataCallback<in InputStream>

    @Mock
    private lateinit var priority: Priority

    private lateinit var inputStreamFetcher: InputStreamFetcher<String>

    @Test
    fun `given an item, when loadData is called, calls use case with given item`() = runBlockingTest {
        val useCase = mockUseCase()
        `when`(useCase.run(ITEM)).thenReturn(Either.Left(ServerError))

        inputStreamFetcher = InputStreamFetcher(ITEM, useCase)

        inputStreamFetcher.loadData(priority, callback)

        verify(useCase).run(eq(ITEM))
    }

    @Test
    fun `given use case is successful, when loadData is called, calls callback's onDataReady with received stream`() {
        val inputStream = mock(InputStream::class.java)
        inputStreamFetcher = InputStreamFetcher(ITEM, successUseCase(inputStream))

        inputStreamFetcher.loadData(priority, callback)

        verify(callback).onDataReady(inputStream)
    }


    @Test
    fun `given use case fails, when loadData is called, calls callback's onLoadFailed with InputStreamFetchException`() {
        inputStreamFetcher = InputStreamFetcher(ITEM, errorUseCase(ServerError))

        inputStreamFetcher.loadData(priority, callback)

        verify(callback).onLoadFailed(InputStreamFetcher.InputStreamFetchException(ServerError))
    }

    @Test
    fun `dataSource is always remote`() {
        inputStreamFetcher = InputStreamFetcher(ITEM, mockUseCase())

        val dataSource = inputStreamFetcher.dataSource

        assertEquals(DataSource.REMOTE, dataSource)
    }

    companion object {
        private const val ITEM = "param"

        private fun mockUseCase() = mock(UseCase::class.java) as UseCase<InputStream, String>

        private fun errorUseCase(failure: Failure) = object : UseCase<InputStream, String> {
            override suspend fun run(params: String): Either<Failure, InputStream> = Either.Left(failure)
        }

        private fun successUseCase(inputStream: InputStream) = object : UseCase<InputStream, String> {
            override suspend fun run(params: String): Either<Failure, InputStream> = Either.Right(inputStream)
        }
    }
}
