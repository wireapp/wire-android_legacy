package com.waz.zclient.user.profile

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.eq
import com.waz.zclient.framework.livedata.observeOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class GetUserProfilePictureDelegateTest : UnitTest() {

    @Mock
    private lateinit var getUserProfilePictureUseCase: GetUserProfilePictureUseCase

    @Mock
    private lateinit var scope: CoroutineScope

    private lateinit var getUserProfilePictureDelegate: GetUserProfilePictureDelegate

    @Before
    fun setUp() {
        getUserProfilePictureDelegate = GetUserProfilePictureDelegate(getUserProfilePictureUseCase)
    }

    @Test
    fun `given a coroutineScope, when fetchProfilePicture is called, then calls useCase with that scope`() {
        getUserProfilePictureDelegate.fetchProfilePicture(scope)

        verify(getUserProfilePictureUseCase).invoke(eq(scope), any(), any())
    }

    @InternalCoroutinesApi
    @Test
    fun `given useCase emits an asset, when fetchProfilePicture is called, then updates profilePicture with that asset`() =
        runBlockingTest {
            val profilePictureAsset = mock(ProfilePictureAsset::class.java)
            val assetFlow = flow {
                emit(profilePictureAsset)
            }
            lenient().`when`(getUserProfilePictureUseCase.run(Unit)).thenReturn(assetFlow)

            getUserProfilePictureDelegate.fetchProfilePicture(scope)

            assetFlow.collect {
                getUserProfilePictureDelegate.profilePicture.observeOnce {
                    it shouldBe profilePictureAsset
                }
            }
        }
}
