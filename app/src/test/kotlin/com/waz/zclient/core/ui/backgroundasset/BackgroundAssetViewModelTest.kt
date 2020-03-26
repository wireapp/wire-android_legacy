package com.waz.zclient.core.ui.backgroundasset

import androidx.lifecycle.viewModelScope
import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import com.waz.zclient.user.profile.GetUserProfilePictureDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.`should be`
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class BackgroundAssetViewModelTest : UnitTest() {

    @Mock
    private lateinit var getUserProfilePictureDelegate: GetUserProfilePictureDelegate

    @Captor
    private lateinit var scopeCaptor: ArgumentCaptor<CoroutineScope>

    private lateinit var backgroundAssetViewModel: BackgroundAssetViewModel

    @Before
    fun setUp() {
        backgroundAssetViewModel = BackgroundAssetViewModel(getUserProfilePictureDelegate)
    }

    @Test
    fun `given a delegate, when backgroundAsset is called, then returns delegate's profile picture`() {
        backgroundAssetViewModel.backgroundAsset

        verify(getUserProfilePictureDelegate).profilePicture
    }

    @Test
    fun `given a delegate, when fetchBackgroundImage is called, then calls delegate's fetchProfilePicture with viewModelScope`() {
        backgroundAssetViewModel.fetchBackgroundAsset()

        verify(getUserProfilePictureDelegate).fetchProfilePicture(capture(scopeCaptor))
        scopeCaptor.value `should be` backgroundAssetViewModel.viewModelScope
    }
}
