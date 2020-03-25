package com.waz.zclient.settings.support

import com.waz.zclient.core.ui.backgroundasset.BackgroundAssetViewModel
import com.waz.zclient.user.profile.GetUserProfilePictureDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsSupportMainViewModel(
    getUserProfilePictureDelegate: GetUserProfilePictureDelegate
) : BackgroundAssetViewModel(getUserProfilePictureDelegate)
