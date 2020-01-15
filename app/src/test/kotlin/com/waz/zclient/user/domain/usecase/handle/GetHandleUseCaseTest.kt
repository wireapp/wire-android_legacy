package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.UnitTest
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
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

        userFlow.map {
            getHandleUseCase.run(Unit).single() shouldBe it.handle
        }
    }

    companion object {
        private const val TEST_HANDLE = "@wire"
    }
}
