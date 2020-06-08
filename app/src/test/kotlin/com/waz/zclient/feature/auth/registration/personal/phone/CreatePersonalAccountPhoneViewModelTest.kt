package com.waz.zclient.feature.auth.registration.personal.phone

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.activation.usecase.PhoneBlacklisted
import com.waz.zclient.shared.activation.usecase.PhoneInUse
import com.waz.zclient.shared.activation.usecase.SendPhoneActivationCodeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountPhoneViewModelTest : UnitTest() {

    private lateinit var phoneViewModel: CreatePersonalAccountPhoneViewModel

    @Mock
    private lateinit var sendPhoneActivationCodeUseCase: SendPhoneActivationCodeUseCase


    @Before
    fun setup() {
        phoneViewModel = CreatePersonalAccountPhoneViewModel(
            sendPhoneActivationCodeUseCase
        )
    }

    @Test
    fun `given sendActivationCode is called, when the phone is blacklisted then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(PhoneBlacklisted))

            phoneViewModel.sendActivationCode(TEST_PHONE)

            val error = phoneViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_phone_phone_blacklisted_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when the phone is in use then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(PhoneInUse))

            phoneViewModel.sendActivationCode(TEST_PHONE)

            val error = phoneViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_phone_phone_in_use_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then a network error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            phoneViewModel.sendActivationCode(TEST_PHONE)

            assertEquals(Unit, phoneViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            phoneViewModel.sendActivationCode(TEST_PHONE)

            assertEquals(Unit, phoneViewModel.sendActivationCodeSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_PHONE = "+499999999"
    }
}
