package com.waz.zclient.user.domain.usecase

import com.waz.zclient.eq
import com.waz.zclient.user.data.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class ChangePhoneUseCaseTest {

    private lateinit var changePhoneUseCase: ChangePhoneUseCase

    @Mock
    private lateinit var userRepository: UsersRepository

    @Mock
    private lateinit var changePhoneParams: ChangePhoneParams

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        changePhoneUseCase = ChangePhoneUseCase(userRepository)
    }

    @Test
    fun `Given update phone use case is executed, then the repository should update phone`() = runBlockingTest {
        `when`(changePhoneParams.phoneNumber).thenReturn(TEST_PHONE)

        changePhoneUseCase.run(changePhoneParams)

        verify(userRepository).changePhone(eq(TEST_PHONE))
    }

    companion object {
        private const val TEST_PHONE = "+499477466343"
    }
}
