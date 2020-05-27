package com.waz.zclient.core.ui.backgroundasset

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.user.profile.GetUserProfilePictureUseCase
import com.waz.zclient.shared.user.profile.ProfilePictureAsset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class BackgroundAssetViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

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
        runBlocking {
            val profilePictureAsset = Mockito.mock(ProfilePictureAsset::class.java)
            `when`(getUserProfilePictureUseCase.run(Unit)).thenReturn(flowOf(profilePictureAsset))

            backgroundAssetViewModel.fetchBackgroundAsset()

            assertEquals(profilePictureAsset, backgroundAssetViewModel.backgroundAsset.awaitValue())
        }
}
