package com.waz.zclient.user.domain.usecase

import com.waz.zclient.eq
import com.waz.zclient.user.data.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class ChangeEmailUseCaseTest {

    private lateinit var changeEmailUseCase: ChangeEmailUseCase

    @Mock
    private lateinit var userRepository: UsersRepository

    @Mock
    private lateinit var changeEmailParams: ChangeEmailParams

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        changeEmailUseCase = ChangeEmailUseCase(userRepository)
    }

    @Test
    fun `Given update email use case is executed, then the repository should update email`() = runBlockingTest {
        `when`(changeEmailParams.email).thenReturn(TEST_EMAIL)

        changeEmailUseCase.run(changeEmailParams)

        verify(userRepository).changeEmail(eq(TEST_EMAIL))
    }

    companion object {
        private const val TEST_EMAIL = "email@wire.com"
    }
}
