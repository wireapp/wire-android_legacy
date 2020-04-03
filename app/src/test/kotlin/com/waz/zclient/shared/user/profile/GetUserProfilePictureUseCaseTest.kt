package com.waz.zclient.shared.user.profile

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class GetUserProfilePictureUseCaseTest : UnitTest() {

    @Mock
    private lateinit var usersRepository: UsersRepository

    @Mock
    private lateinit var profilePictureMapper: ProfilePictureMapper

    private lateinit var useCase: GetUserProfilePictureUseCase

    @Before
    fun setUp() {
        useCase = GetUserProfilePictureUseCase(usersRepository, profilePictureMapper)
    }

    @Test
    fun `given a user with no profile picture, when run is called, do not emit anything`() =
        runBlockingTest {
            val user = mock(User::class.java)
            `when`(user.picture).thenReturn(null)
            `when`(usersRepository.profileDetails()).thenReturn(flow {
                emit(user)
            })

            val assetFlow = useCase.run(Unit)
            assetFlow.collect()

            verify(profilePictureMapper, never()).map(any())
        }

    @Test
    fun `given a user with a profile picture, when run is called, emit profile picture via mapper`() =
        runBlockingTest {
            val user = mock(User::class.java)
            val assetId = "picture"
            `when`(user.picture).thenReturn(assetId)
            `when`(usersRepository.profileDetails()).thenReturn(flow {
                emit(user)
            })

            val assetFlow = useCase.run(Unit)
            assetFlow.collect()

            verify(profilePictureMapper).map(assetId)
        }
}
