package com.waz.zclient.feature.auth.registration.register.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.feature.auth.registration.register.RegisterRepository
import com.waz.zclient.feature.auth.registration.register.datasources.remote.RegisterRemoteDataSource
import com.waz.zclient.feature.auth.registration.register.datasources.remote.UserResponse
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class RegisterDataSourceTest : UnitTest() {

    @get:Rule
    val testRule = CoroutinesTestRule()

    private lateinit var registerDataSource: RegisterRepository

    @Mock
    private lateinit var registerRemoteDataSource: RegisterRemoteDataSource

    @Mock
    private lateinit var userResponse: UserResponse

    @Before
    fun setup() {
        registerDataSource = RegisterDataSource(registerRemoteDataSource)
    }

    @Test
    fun `Given registerPersonalAccountWithEmail() is called and remote request fails then return failure`() =
        runBlocking {

            `when`(registerRemoteDataSource.registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(ServerError))

            val response = registerDataSource.registerPersonalAccountWithEmail(
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

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given registerPersonalAccountWithEmail() is called and remote request is success, then return success`() =
        runBlocking {

            `when`(registerRemoteDataSource.registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Right(userResponse))

            val response = registerDataSource.registerPersonalAccountWithEmail(
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

            response.onSuccess {
                assertEquals(Unit, it)
            }
            assertTrue(response.isRight)
        }

    @Test
    fun `Given registerPersonalAccountWithPhone() is called and remote request fails then return failure`() =
        runBlocking {

            `when`(registerRemoteDataSource.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(ServerError))

            val response = registerDataSource.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            verify(registerRemoteDataSource).registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given registerPersonalAccountWithPhone() is called and remote request is success, then return success`() =
        runBlocking {

            `when`(registerRemoteDataSource.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Right(userResponse))

            val response = registerDataSource.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            verify(registerRemoteDataSource).registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            response.onSuccess {
                assertEquals(Unit, it)
            }
            assertTrue(response.isRight)
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_ACTIVATION_CODE = "000000"
        private const val TEST_PHONE = "+499999999"
    }

}
