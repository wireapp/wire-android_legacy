package com.waz.zclient.feature.settings.devices.detail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.clients.Client
import com.waz.zclient.shared.clients.ClientLocation
import com.waz.zclient.shared.clients.usecase.GetClientUseCase
import com.waz.zclient.shared.clients.usecase.GetSpecificClientParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class SettingsDeviceDetailViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: SettingsDeviceDetailViewModel

    @Mock
    private lateinit var getClientUseCase: GetClientUseCase

    @Before
    fun setup() {
        viewModel = SettingsDeviceDetailViewModel(getClientUseCase)
    }

    @Test
    fun `given data is loaded successfully, then assert data is mapped correctly`() {
        val params = GetSpecificClientParams(TEST_ID)
        val location = Mockito.mock<ClientLocation>(ClientLocation::class.java)
        val client = Client(time = TEST_TIME, label = TEST_LABEL, clazz = TEST_CLASS, type = TEST_TYPE, id = TEST_ID, model = TEST_MODEL, location = location)

        runBlocking {
            `when`(getClientUseCase.run(params)).thenReturn(Either.Right(client))

            viewModel.loading.observeOnce { assertTrue(it) }

            viewModel.loadData(TEST_ID)

            with(viewModel.currentDevice.awaitValue().client) {
                assertEquals(viewModel.loading.value, false)
                assertEquals(TEST_LABEL, label)
                assertEquals(TEST_TIME, time)
                assertEquals(TEST_ID, id)
            }
        }
    }

    @Test
    fun `given data source returns NetworkError, then update error live data`() {
        val params = GetSpecificClientParams(TEST_ID)
        runBlocking {
            `when`(getClientUseCase.run(params)).thenReturn(Either.Left(NetworkConnection))

            viewModel.loading.observeOnce { isLoading ->
                assertTrue(isLoading)
            }

            viewModel.loadData(TEST_ID)

            viewModel.error.awaitValue()
            assertTrue(viewModel.loading.value == false)
            //TODO update loading live data scenario when it has been confirmed
        }
    }

    @Test
    fun `given data source returns ServerError, then update error live data`() {
        val params = GetSpecificClientParams(TEST_ID)
        runBlocking {
            `when`(getClientUseCase.run(params)).thenReturn(Either.Left(ServerError))

            viewModel.loading.observeOnce { isLoading ->
                assertTrue(isLoading)
            }

            viewModel.loadData(TEST_ID)

            viewModel.error.awaitValue()
            assertTrue(viewModel.loading.value == false)
            //TODO update loading live data scenario when it has been confirmed
        }
    }

    @Test
    fun `given data source returns CancellationError, then update error live data`() {
        runBlocking {
            `when`(getClientUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            viewModel.loading.observeOnce { isLoading ->
                assertTrue(isLoading)
            }

            viewModel.loadData(TEST_ID)

            viewModel.error.awaitValue()
            assertTrue(viewModel.loading.value == false)
            //TODO update loading live data scenario when it has been confirmed
        }
    }

    companion object {
        private const val TEST_TIME = "2019-11-14T11:00:42.482Z"
        private const val TEST_LABEL = "Tester's phone"
        private const val TEST_CLASS = "phone"
        private const val TEST_TYPE = "permanant"
        private const val TEST_ID = "4555f7b2"
        private const val TEST_MODEL = "Samsung"
    }
}
