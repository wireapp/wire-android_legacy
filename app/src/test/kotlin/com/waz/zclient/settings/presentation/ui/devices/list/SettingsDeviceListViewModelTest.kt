package com.waz.zclient.settings.presentation.ui.devices.list

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetCurrentDeviceUseCase
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

    @Mock
    private lateinit var getCurrentDeviceUseCase: GetCurrentDeviceUseCase

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        viewModel = SettingsDeviceListViewModel(getAllClientsUseCase, getCurrentDeviceUseCase)
    }

    @Test
    fun `given data is loaded successfully, when list is full, then assert data is mapped correctly`() {

        val location = mock<ClientLocation>(ClientLocation::class.java)
        val client = Client(TEST_COOKIE, TEST_TIME, TEST_LABEL, TEST_CLASS, TEST_TYPE, TEST_ID, TEST_MODEL, location)

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
    fun `given data is loaded successfully, when list is full, then assert list state is empty`() {

        runBlocking { `when`(getAllClientsUseCase.run(Unit)).thenReturn(Either.Right(listOf())) }

        viewModel.loadData()

        viewModel.loading.observeOnce { isLoading ->
            assert(isLoading)
        }

        viewModel.otherDevices.observeOnce {
            assert(viewModel.loading.value == false)
            assert(it.isEmpty())
        }
    }

    @Test
    fun `given data isn't loaded successfully, then update error live data`() {

        runBlocking { `when`(getAllClientsUseCase.run(Unit)).thenReturn(Either.Left(Failure(TEST_ERROR_MESSAGE))) }

        viewModel.loadData()

        viewModel.loading.observeOnce { isLoading ->
            assert(isLoading)
        }

        viewModel.error.observeOnce {
            assert(viewModel.loading.value == false)
            assert(it == TEST_ERROR_MESSAGE)
        }
    }

    companion object {
        private const val TEST_ERROR_MESSAGE = "Something went wrong, please try again."
        private const val TEST_COOKIE = "4555f7b2"
        private const val TEST_TIME = "2019-11-14T11:00:42.482Z"
        private const val TEST_LABEL = "Tester's phone"
        private const val TEST_CLASS = "phone"
        private const val TEST_TYPE = "permanant"
        private const val TEST_ID = "4555f7b2"
        private const val TEST_MODEL = "Samsung"
    }
}
