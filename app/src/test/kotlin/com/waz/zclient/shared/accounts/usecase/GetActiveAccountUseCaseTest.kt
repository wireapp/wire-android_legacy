package com.waz.zclient.shared.accounts.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.mock
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient
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
    fun `given use-case is executed, when active account exists in list, then should return filteredAccount`() = runBlockingTest {
        val mockListOfAccounts = successListOfAccounts()

        `when`(userRepository.currentUserId()).thenReturn(Either.Right(TEST_ACTIVE_USER_ID))
        `when`(accountsRepository.activeAccounts()).thenReturn(mockListOfAccounts)

        val result = getActiveAccountUseCase.run(Unit)

        result.isRight shouldBe true
    }

    @Test
    fun `given use-case is executed, when active account does not exist in list, then should return error`() = runBlockingTest {
        val mockListOfAccounts = successListOfAccounts()

        `when`(userRepository.currentUserId()).thenReturn(Either.Right(TEST_NON_ACTIVE_USER_ID))
        `when`(accountsRepository.activeAccounts()).thenReturn(mockListOfAccounts)

        val result = getActiveAccountUseCase.run(Unit)

        result.isLeft shouldBe true
    }

    @Test
    fun `given use-case is executed, when failure is returned from data layer, then return failure`() = runBlockingTest {
        `when`(accountsRepository.activeAccounts()).thenReturn(Either.Left(ServerError))

        val result = getActiveAccountUseCase.run(Unit)

        result.isLeft shouldBe true

        verifyNoInteractions(userRepository)
    }

    private fun successListOfAccounts(): Either<Failure, List<ActiveAccount>>? {
        val activeAccount = mock(ActiveAccount::class)
        lenient().`when`(activeAccount.id).thenReturn(TEST_ACTIVE_USER_ID)
        return Either.Right(listOf(activeAccount))
    }

    companion object {
        private const val TEST_NON_ACTIVE_USER_ID = "wrongUserId"
        private const val TEST_ACTIVE_USER_ID = "testUserId"
    }
}
