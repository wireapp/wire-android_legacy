package com.waz.zclient.shared.user.handle.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class GetHandleUseCaseTest : UnitTest() {

    private lateinit var getHandleUseCase: GetHandleUseCase

    @Mock
    private lateinit var usersRepository: UsersRepository

    @Mock
    private lateinit var user: User

    @Before
    fun setup() {
        getHandleUseCase = GetHandleUseCase(usersRepository)
    }

    @Test
    fun `Given get handle use case is executed, then the repository should retrieve profile details`() = runBlockingTest {
        `when`(usersRepository.profileDetails()).thenReturn(flowOf(user))
        `when`(user.handle).thenReturn(TEST_HANDLE)

        val handleFlow = getHandleUseCase.run(Unit)

        handleFlow.collect {
            assertEquals(TEST_HANDLE, it)
        }
    }

    companion object {
        private const val TEST_HANDLE = "@wire"
    }
}
