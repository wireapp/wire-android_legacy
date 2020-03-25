package com.waz.zclient.features.settings.main

import com.waz.zclient.core.ui.backgroundasset.BackgroundAssetViewModel
import com.waz.zclient.user.profile.GetUserProfilePictureDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsMainViewModel(
    getUserProfilePictureDelegate: GetUserProfilePictureDelegate
) : BackgroundAssetViewModel(getUserProfilePictureDelegate)
