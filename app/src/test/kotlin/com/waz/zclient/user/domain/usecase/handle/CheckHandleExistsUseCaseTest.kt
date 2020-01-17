package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.user.data.UsersRepository
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
    private lateinit var userRepository: UsersRepository

    @Mock
    private lateinit var checkHandleExistsParams: CheckHandleExistsParams

    @Before
    fun setup() {
        checkHandleExistsUseCase = CheckHandleExistsUseCase(userRepository)
    }

    @Test
    fun `Given check handle exists use case is executed, then the repository check if the handle exists`() = runBlockingTest {
        `when`(checkHandleExistsParams.newHandle).thenReturn(TEST_HANDLE)

        checkHandleExistsUseCase.run(checkHandleExistsParams)

        verify(userRepository).doesHandleExist(eq(TEST_HANDLE))
    }

    companion object {
        private const val TEST_HANDLE = "@wire"
    }
}
