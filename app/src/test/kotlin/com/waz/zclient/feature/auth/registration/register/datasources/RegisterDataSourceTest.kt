package com.waz.zclient.feature.auth.registration.register.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.register.RegisterRepository
import com.waz.zclient.feature.auth.registration.register.datasources.remote.RegisterRemoteDataSource
import com.waz.zclient.feature.auth.registration.register.datasources.remote.UserResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class RegisterDataSourceTest : UnitTest() {

    private lateinit var registerRepository: RegisterRepository

    @Mock
    private lateinit var registerRemoteDataSource: RegisterRemoteDataSource

    @Mock
    private lateinit var userResponse: UserResponse

    @Before
    fun setup() {
        registerRepository = RegisterDataSource(registerRemoteDataSource)
    }

    @Test
    fun `Given registerPersonalAccountWithEmail() is called and remote request fails then return failure`() = runBlockingTest {

        `when`(registerRemoteDataSource.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )).thenReturn(Either.Left(ServerError))

        val response = registerRepository.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        verify(registerRemoteDataSource).registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        response.isLeft shouldBe true
    }

    @Test
    fun `Given registerPersonalAccountWithEmail() is called and remote request is success, then return success`() = runBlockingTest {

        `when`(registerRemoteDataSource.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )).thenReturn(Either.Right(userResponse))

        val response = registerRepository.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        verify(registerRemoteDataSource).registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        response.isRight shouldBe true
    }


    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_ACTIVATION_CODE = "000000"
    }

}
