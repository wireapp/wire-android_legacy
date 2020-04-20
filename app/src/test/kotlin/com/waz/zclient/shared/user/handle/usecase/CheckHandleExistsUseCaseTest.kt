package com.waz.zclient.shared.user.handle.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.shared.user.handle.UserHandleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class CheckHandleExistsUseCaseTest : UnitTest() {

    private lateinit var checkHandleExistsUseCase: CheckHandleExistsUseCase

    @Mock
    private lateinit var handleRepository: UserHandleRepository

    @Mock
    private lateinit var checkHandleExistsParams: CheckHandleExistsParams

    @Before
    fun setup() {
        checkHandleExistsUseCase = CheckHandleExistsUseCase(handleRepository)
    }

    @Test
    fun `Given check handle exists use case is executed, then the repository check if the handle exists`() = runBlockingTest {
        `when`(checkHandleExistsParams.newHandle).thenReturn(TEST_HANDLE)

        checkHandleExistsUseCase.run(checkHandleExistsParams)

        verify(handleRepository).doesHandleExist(eq(TEST_HANDLE))
    }

    companion object {
        private const val TEST_HANDLE = "@wire"
    }
}
