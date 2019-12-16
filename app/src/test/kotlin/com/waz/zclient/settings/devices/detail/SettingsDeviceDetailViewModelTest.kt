package com.waz.zclient.settings.devices.detail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.devices.domain.GetSpecificClientParams
import com.waz.zclient.devices.domain.GetSpecificClientUseCase
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.domain.model.ClientLocation
import com.waz.zclient.framework.livedata.observeOnce
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class SettingsDeviceDetailViewModelTest {

    private lateinit var viewModel: SettingsDeviceDetailViewModel

    @Mock
    private lateinit var getSpecificClientUseCase: GetSpecificClientUseCase

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        viewModel = SettingsDeviceDetailViewModel(getSpecificClientUseCase)
    }

    @Test
    fun `given data is loaded successfully, then assert data is mapped correctly`() {
        val params = GetSpecificClientParams(TEST_ID)
        val location = Mockito.mock<ClientLocation>(ClientLocation::class.java)
        val client = Client(TEST_COOKIE, TEST_TIME, TEST_LABEL, TEST_CLASS, TEST_TYPE, TEST_ID, TEST_MODEL, TEST_VERIFICATION, location)

        runBlocking { Mockito.`when`(getSpecificClientUseCase.run(params)).thenReturn(Either.Right(client)) }

        viewModel.loadData(TEST_ID)

        viewModel.loading.observeOnce { isLoading ->
            assert(isLoading)
        }

        viewModel.currentDevice.observeOnce {
            val clientItem = it.client
            assert(viewModel.loading.value == false)
            assert(clientItem.label == TEST_LABEL)
            assert(clientItem.time == TEST_TIME)
            assert(clientItem.id == TEST_ID)
        }

    }

    @Test
    fun `given data source returns NetworkError, then update error live data`() {
        val params = GetSpecificClientParams(TEST_ID)
        runBlocking { Mockito.`when`(getSpecificClientUseCase.run(params)).thenReturn(Either.Left(Failure.NetworkConnection)) }

        viewModel.loadData(TEST_ID)

        viewModel.loading.observeOnce { isLoading ->
            assert(isLoading)
        }

        viewModel.error.observeOnce {
            assert(viewModel.loading.value == false)
            //TODO update loading live data scenario when it has been confirmed
        }
    }

    @Test
    fun `given data source returns ServerError, then update error live data`() {
        val params = GetSpecificClientParams(TEST_ID)
        runBlocking { `when`(getSpecificClientUseCase.run(params)).thenReturn(Either.Left(Failure.ServerError(TEST_CODE, TEST_ERROR_MESSAGE))) }

        viewModel.loadData(TEST_ID)

        viewModel.loading.observeOnce { isLoading ->
            assert(isLoading)
        }

        viewModel.error.observeOnce {
            assert(viewModel.loading.value == false)
            //TODO update loading live data scenario when it has been confirmed
        }
    }

    @Test
    fun `given data source returns CancellationError, then update error live data`() {
        val params = GetSpecificClientParams(TEST_ID)
        runBlocking { Mockito.`when`(getSpecificClientUseCase.run(params)).thenReturn(Either.Left(Failure.CancellationError)) }

        viewModel.loadData(TEST_ID)

        viewModel.loading.observeOnce { isLoading ->
            assert(isLoading)
        }

        viewModel.error.observeOnce {
            assert(viewModel.loading.value == false)
            //TODO update loading live data scenario when it has been confirmed
        }
    }

    companion object {
        private const val TEST_CODE = 401
        private const val TEST_ERROR_MESSAGE = "Something went wrong, please try again."
        private const val TEST_COOKIE = "4555f7b2"
        private const val TEST_TIME = "2019-11-14T11:00:42.482Z"
        private const val TEST_LABEL = "Tester's phone"
        private const val TEST_CLASS = "phone"
        private const val TEST_TYPE = "permanant"
        private const val TEST_ID = "4555f7b2"
        private const val TEST_MODEL = "Samsung"
        private const val TEST_VERIFICATION = "VERIFIED"
    }
}
