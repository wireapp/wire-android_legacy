package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.UnitTest
import com.waz.zclient.feature.auth.registration.personal.email.EmailCredentialsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EmailCredentialsViewModelTest : UnitTest() {

    private lateinit var createPersonalAccountWithEmailSharedViewModel: EmailCredentialsViewModel

    @Before
    fun setup() {
        createPersonalAccountWithEmailSharedViewModel = EmailCredentialsViewModel()
    }

    @Test
    fun `given saveEmail() is called, then the email should be added to the credentials`() =
        runBlockingTest {

            createPersonalAccountWithEmailSharedViewModel.saveEmail(TEST_EMAIL)

            createPersonalAccountWithEmailSharedViewModel.email() shouldBe TEST_EMAIL
        }

    @Test
    fun `given saveActivationCode() is called, then the activation code should be added to the credentials`() =
        runBlockingTest {

            createPersonalAccountWithEmailSharedViewModel.saveActivationCode(TEST_CODE)

            createPersonalAccountWithEmailSharedViewModel.activationCode() shouldBe TEST_CODE
        }

    @Test
    fun `given saveName() is called, then the name should be added to the credentials`() =
        runBlockingTest {

            createPersonalAccountWithEmailSharedViewModel.saveName(TEST_NAME)

            createPersonalAccountWithEmailSharedViewModel.name() shouldBe TEST_NAME
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
    }
}
