package com.waz.zclient.settings.devices.list

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.waz.zclient.core.exception.HttpError
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.domain.model.ClientLocation
import com.waz.zclient.framework.livedata.observeOnce
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

class SettingsDeviceListViewModelTest {

    private lateinit var viewModel: SettingsDeviceListViewModel

    @Mock
    private lateinit var getAllClientsUseCase: GetAllClientsUseCase

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        viewModel = SettingsDeviceListViewModel(getAllClientsUseCase)
    }

    @Test
    fun `given data is loaded successfully, when list is populated, then assert data is mapped correctly`() {

        val location = mock<ClientLocation>(ClientLocation::class.java)
        val client = Client(time = TEST_TIME, label = TEST_LABEL, type = TEST_TYPE, id = TEST_ID, _class = TEST_CLASS, model = TEST_MODEL, location = location)

        runBlocking { `when`(getAllClientsUseCase.run(Unit)).thenReturn(Either.Right(listOf(client))) }

        viewModel.loadData()

        viewModel.loading.observeOnce { isLoading ->
            assert(isLoading)
        }

        viewModel.otherDevices.observeOnce {
            val clientItem = it[0].client
            assert(viewModel.loading.value == false)
            assert(clientItem.label == TEST_LABEL)
            assert(clientItem.time == TEST_TIME)
            assert(clientItem.id == TEST_ID)
            assert(it.size == 1)
        }

    }

    @Test
    fun `given data source returns NetworkError, then update error live data`() {
        runBlocking { `when`(getAllClientsUseCase.run(Unit)).thenReturn(Either.Left(NetworkConnection)) }

        viewModel.loadData()

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
        runBlocking { `when`(getAllClientsUseCase.run(Unit)).thenReturn(Either.Left(HttpError(TEST_CODE, TEST_ERROR_MESSAGE))) }


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
        runBlocking { `when`(getAllClientsUseCase.run(Unit)).thenReturn(Either.Left(NetworkConnection)) }

        viewModel.loadData()

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
        private const val TEST_TIME = "2019-11-14T11:00:42.482Z"
        private const val TEST_LABEL = "Tester's phone"
        private const val TEST_CLASS = "phone"
        private const val TEST_TYPE = "permanant"
        private const val TEST_ID = "4555f7b2"
        private const val TEST_MODEL = "Samsung"
    }
}
