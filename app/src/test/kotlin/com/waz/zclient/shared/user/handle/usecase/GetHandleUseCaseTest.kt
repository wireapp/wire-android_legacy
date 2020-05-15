package com.waz.zclient.shared.user.handle.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.lenient

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class GetHandleUseCaseTest : UnitTest() {

    private lateinit var getHandleUseCase: GetHandleUseCase

    @Mock
    private lateinit var usersRepository: UsersRepository

    @Mock
    private lateinit var user: User

    private lateinit var userFlow: Flow<User>

    @Before
    fun setup() {
        getHandleUseCase = GetHandleUseCase(usersRepository)
        userFlow = flow { user }
    }

    @Test
    fun `Given get handle use case is executed, then the repository should retrieve profile details`() = runBlockingTest {
        lenient().`when`(usersRepository.profileDetails()).thenReturn(userFlow)
        lenient().`when`(user.handle).thenReturn(TEST_HANDLE)

        getHandleUseCase.run(Unit)

        userFlow.mapLatest {
            getHandleUseCase.run(Unit).collect {
                it shouldBe TEST_HANDLE
            }
        }
    }

    companion object {
        private const val TEST_HANDLE = "@wire"
    }
}
