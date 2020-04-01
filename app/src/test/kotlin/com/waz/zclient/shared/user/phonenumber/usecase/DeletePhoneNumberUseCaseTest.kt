package com.waz.zclient.shared.user.phonenumber.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.user.phonenumber.PhoneNumberRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito

@ExperimentalCoroutinesApi
class DeletePhoneNumberUseCaseTest : UnitTest() {

    private lateinit var deletePhoneNumberUseCase: DeletePhoneNumberUseCase

    @Mock
    private lateinit var phoneNumberRepository: PhoneNumberRepository

    @Before
    fun setup() {
        deletePhoneNumberUseCase = DeletePhoneNumberUseCase(phoneNumberRepository)
    }

    @Test
    fun `Given delete phone use case is executed, then the repository should delete phone number`() = runBlockingTest {
        deletePhoneNumberUseCase.run(Unit)

        Mockito.verify(phoneNumberRepository).deletePhone()
    }
}
