package com.waz.zclient.user.profile

import com.waz.zclient.UnitTest
import com.waz.zclient.user.UsersRepository
import com.waz.zclient.user.profile.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class GetUserProfileUseCaseTest : UnitTest() {

    private lateinit var getUserProfileUseCase: GetUserProfileUseCase

    @Mock
    private lateinit var userRepository: UsersRepository

    @Before
    fun setup() {
        getUserProfileUseCase = GetUserProfileUseCase(userRepository)
    }

    @Test
    fun `Given get profile details use case is executed, then the repository should retrieve profile details`() = runBlockingTest {
        getUserProfileUseCase.run(Unit)

        verify(userRepository).profileDetails()
    }
}
