package com.waz.zclient.user.domain.usecase

import com.waz.zclient.user.data.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class GetUserProfileUseCaseTest {

    private lateinit var getUserProfileUseCase: GetUserProfileUseCase

    @Mock
    private lateinit var userRepository: UsersRepository

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        getUserProfileUseCase = GetUserProfileUseCase(userRepository)
    }

    @Test
    fun `Given get profile details use case is executed, then the repository should retrieve profile details`() = runBlockingTest {
        getUserProfileUseCase.run(Unit)

        Mockito.verify(userRepository).profileDetails()
    }
}
