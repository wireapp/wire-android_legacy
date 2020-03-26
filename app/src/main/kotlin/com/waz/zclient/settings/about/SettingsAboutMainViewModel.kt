package com.waz.zclient.settings.about

import com.waz.zclient.core.ui.backgroundasset.BackgroundAssetViewModel
import com.waz.zclient.user.profile.GetUserProfilePictureDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsAboutMainViewModel(
    getUserProfilePictureDelegate: GetUserProfilePictureDelegate
) : BackgroundAssetViewModel(getUserProfilePictureDelegate)
