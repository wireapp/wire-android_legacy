package com.waz.zclient.feature.auth.registration.personal.phone

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class PhoneCredentialsViewModelTest : UnitTest() {

    @get:Rule
    val testRule = CoroutinesTestRule()

    private lateinit var phoneCredentialsViewModel: CreatePersonalAccountPhoneCredentialsViewModel

    @Before
    fun setup() {
        phoneCredentialsViewModel = CreatePersonalAccountPhoneCredentialsViewModel()
    }

    @Test
    fun `given savePhone() is called, then the phone should be added to the credentials`() =
        runBlocking {

            phoneCredentialsViewModel.savePhone(TEST_PHONE)

            assertEquals(TEST_PHONE, phoneCredentialsViewModel.phone())
        }

    @Test
    fun `given saveActivationCode() is called, then the activation code should be added to the credentials`() =
        runBlocking {

            phoneCredentialsViewModel.saveActivationCode(TEST_CODE)

            assertEquals(TEST_CODE, phoneCredentialsViewModel.activationCode())
        }

    companion object {
        private const val TEST_PHONE = "+499999999"
        private const val TEST_CODE = "000000"
    }
}
