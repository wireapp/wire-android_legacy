package com.waz.zclient.shared.accounts.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.mock
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@ExperimentalCoroutinesApi
class GetActiveAccountUseCaseTest : UnitTest() {

    private lateinit var getActiveAccountUseCase: GetActiveAccountUseCase

    @Mock
    private lateinit var accountsRepository: AccountsRepository

    @Mock
    private lateinit var userRepository: UsersRepository

    @Before
    fun setup() {
        getActiveAccountUseCase = GetActiveAccountUseCase(accountsRepository, userRepository)
    }

    @Test
    fun `given use-case is executed, when currentUserId is empty, then returns CannotFindActiveAccount directly`() =
        runBlockingTest {
            `when`(userRepository.currentUserId()).thenReturn(String.empty())

            val result = getActiveAccountUseCase.run(Unit)

            result.isLeft shouldBe true
            result.onFailure {
                it shouldBe CannotFindActiveAccount
            }
            verifyNoInteractions(accountsRepository)
        }

    @Test
    fun `given use-case is executed, when an active account with id exists in database, then return that account`() =
        runBlockingTest {
            val activeAccount = mock(ActiveAccount::class)

            `when`(userRepository.currentUserId()).thenReturn(TEST_USER_ID)
            `when`(accountsRepository.activeAccountById(TEST_USER_ID)).thenReturn(Either.Right(activeAccount))

            val result = getActiveAccountUseCase.run(Unit)

            result.isRight shouldBe true
            result.map {
                it shouldBe activeAccount
            }
            verify(accountsRepository).activeAccountById(TEST_USER_ID)
        }

    @Test
    fun `given use-case is executed, when no active account with id exists in database, then return CannotFindActiveAccount`() =
        runBlockingTest {
            `when`(userRepository.currentUserId()).thenReturn(TEST_USER_ID)
            `when`(accountsRepository.activeAccountById(TEST_USER_ID)).thenReturn(Either.Right(null))

            val result = getActiveAccountUseCase.run(Unit)

            result.isLeft shouldBe true
            result.onFailure {
                it shouldBe CannotFindActiveAccount
            }
            verify(accountsRepository).activeAccountById(TEST_USER_ID)
        }

    @Test
    fun `given use-case is executed, when failure is returned from data layer, then return failure`() =
        runBlockingTest {
            val failure = mock(Failure::class)
            `when`(userRepository.currentUserId()).thenReturn(TEST_USER_ID)
            `when`(accountsRepository.activeAccountById(TEST_USER_ID)).thenReturn(Either.Left(failure))

            val result = getActiveAccountUseCase.run(Unit)

            result.isLeft shouldBe true
            result.onFailure {
                it shouldBe failure
            }
        }

    companion object {
        private const val TEST_USER_ID = "testUserId"
    }
}
