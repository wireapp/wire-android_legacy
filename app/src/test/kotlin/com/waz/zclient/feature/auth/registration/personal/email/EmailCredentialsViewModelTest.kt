package com.waz.zclient.feature.auth.registration.personal.email

import com.waz.zclient.UnitTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EmailCredentialsViewModelTest : UnitTest() {

    private lateinit var emailCredentialsViewModel: CreatePersonalAccountEmailCredentialsViewModel

    @Before
    fun setup() {
        emailCredentialsViewModel = CreatePersonalAccountEmailCredentialsViewModel()
    }

    @Test
    fun `given saveEmail() is called, then the email should be added to the credentials`() =
        runBlocking {

            emailCredentialsViewModel.saveEmail(TEST_EMAIL)

            assertEquals(TEST_EMAIL, emailCredentialsViewModel.email())
        }

    @Test
    fun `given saveActivationCode() is called, then the activation code should be added to the credentials`() =
        runBlocking {

            emailCredentialsViewModel.saveActivationCode(TEST_CODE)

            assertEquals(TEST_CODE, emailCredentialsViewModel.activationCode())
        }

    @Test
    fun `given saveName() is called, then the name should be added to the credentials`() =
        runBlocking {

            emailCredentialsViewModel.saveName(TEST_NAME)

            assertEquals(TEST_NAME, emailCredentialsViewModel.name())

        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
    }
}
