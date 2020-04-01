package com.waz.zclient.core.ui.backgroundasset

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.user.profile.GetUserProfilePictureUseCase
import com.waz.zclient.shared.user.profile.ProfilePictureAsset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito

@ExperimentalCoroutinesApi
class BackgroundAssetViewModelTest : UnitTest() {

    @Mock
    private lateinit var getUserProfilePictureUseCase: GetUserProfilePictureUseCase

    private lateinit var backgroundAssetViewModel: BackgroundAssetViewModel

    @Before
    fun setUp() {
        backgroundAssetViewModel = BackgroundAssetViewModel(getUserProfilePictureUseCase)
    }

    @InternalCoroutinesApi
    @Test
    fun `given useCase emits an asset, when fetchProfilePicture is called, then updates profilePicture with that asset`() =
        runBlockingTest {
            val profilePictureAsset = Mockito.mock(ProfilePictureAsset::class.java)
            val assetFlow = flow {
                emit(profilePictureAsset)
            }
            Mockito.lenient().`when`(getUserProfilePictureUseCase.run(Unit)).thenReturn(assetFlow)

            backgroundAssetViewModel.fetchBackgroundAsset()

            assetFlow.collect {
                backgroundAssetViewModel.backgroundAsset.observeOnce {
                    it shouldBe profilePictureAsset
                }
            }
        }
}
