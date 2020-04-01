package com.waz.zclient.shared.user.phonenumber.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.shared.user.phonenumber.PhoneNumberRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ChangePhoneNumberUseCaseTest : UnitTest() {

    private lateinit var changePhoneNumberUseCase: ChangePhoneNumberUseCase

    @Mock
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Mock
    private lateinit var changePhoneParams: ChangePhoneNumberParams

    @Before
    fun setup() {
        changePhoneNumberUseCase = ChangePhoneNumberUseCase(phoneNumberRepository)
    }

    @Test
    fun `Given update phone use case is executed, then the repository should update phone`() = runBlockingTest {
        `when`(changePhoneParams.newPhoneNumber).thenReturn(TEST_PHONE)

        changePhoneNumberUseCase.run(changePhoneParams)

        verify(phoneNumberRepository).changePhone(eq(TEST_PHONE))
    }

    companion object {
        private const val TEST_PHONE = "+499477466343"
    }
}
