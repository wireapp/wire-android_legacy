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
class ChangeHandleUseCaseTest : UnitTest() {

    private lateinit var changeHandleUseCase: ChangeHandleUseCase

    @Mock
    private lateinit var handleRepository: UserHandleRepository

    @Mock
    private lateinit var changeHandleParams: ChangeHandleParams

    @Before
    fun setup() {
        changeHandleUseCase = ChangeHandleUseCase(handleRepository)
    }

    @Test
    fun `Given update handle use case is executed, then the repository should update handle`() = runBlockingTest {
        `when`(changeHandleParams.newHandle).thenReturn(TEST_HANDLE)

        changeHandleUseCase.run(changeHandleParams)

        verify(handleRepository).changeHandle(eq(TEST_HANDLE))
    }

    companion object {
        private const val TEST_HANDLE = "@wire"
    }

}
