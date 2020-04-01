package com.waz.zclient.shared.user.email

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ChangeEmailUseCaseTest : UnitTest() {

    private lateinit var changeEmailUseCase: ChangeEmailUseCase

    @Mock
    private lateinit var userRepository: UsersRepository

    @Mock
    private lateinit var changeEmailParams: ChangeEmailParams

    @Before
    fun setup() {
        changeEmailUseCase = ChangeEmailUseCase(userRepository)
    }

    @Test
    fun `Given update email use case is executed, then the repository should update email`() = runBlockingTest {
        `when`(changeEmailParams.newEmail).thenReturn(TEST_EMAIL)

        changeEmailUseCase.run(changeEmailParams)

        verify(userRepository).changeEmail(eq(TEST_EMAIL))
    }

    companion object {
        private const val TEST_EMAIL = "email@wire.com"
    }

}
