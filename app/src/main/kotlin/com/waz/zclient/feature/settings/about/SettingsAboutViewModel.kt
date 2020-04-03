package com.waz.zclient.feature.settings.about

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.config.AppDetailsConfig
import com.waz.zclient.core.config.HostUrlConfig
import com.waz.zclient.core.extension.empty
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.profile.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsAboutViewModel(
    private val appDetailsConfig: AppDetailsConfig,
    private val hostUrlConfig: HostUrlConfig,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    private var _urlLiveData = MutableLiveData<AboutUrl>()
    private var _versionDetailsLiveData = MutableLiveData<VersionDetails>()

    val urlLiveData: LiveData<AboutUrl> = _urlLiveData
    val versionDetailsLiveData: LiveData<VersionDetails> = _versionDetailsLiveData

    fun onAboutButtonClicked() {
        _urlLiveData.value = AboutUrl(generateUrl(String.empty()))
    }

    fun onPrivacyButtonClicked() {
        _urlLiveData.value = AboutUrl(generateUrl(PRIVACY_POLICY_URL_SUFFIX))
    }

    fun onThirdPartyLicenseButtonClicked() {
        _urlLiveData.value = AboutUrl(generateUrl(THIRD_PARTY_LICENSES_URL_SUFFIX))
    }

    fun onTermsButtonClicked() {
        getUserProfileUseCase(viewModelScope, Unit) {
            it.fold({}, ::updateProfileData)
        }
    }

    private var versionClickCount = 0
    fun onVersionButtonClicked() {
        versionClickCount++
        if (versionClickCount >= VERSION_INFO_CLICK_LIMIT) {
            _versionDetailsLiveData.value = VersionDetails(
                R.string.avs_version,
                R.string.audio_notifications_version,
                WIRE_TRANSLATION_VERSION_ID,
                appDetailsConfig.versionDetails
            )
        }
    }

    private fun updateProfileData(user: User) {
        _urlLiveData.value = if (user.teamId.isNullOrEmpty()) {
            AboutUrl(generateUrl(PERSONAL_TERMS_AND_CONDITIONS_SUFFIX))
        } else AboutUrl(generateUrl(TEAM_TERMS_AND_CONDITIONS_SUFFIX))
    }

    private fun generateUrl(urlSuffix: String): String =
        if (urlSuffix.isEmpty()) hostUrlConfig.url
        else hostUrlConfig.url.plus(urlSuffix)

    companion object {
        private const val VERSION_INFO_CLICK_LIMIT = 10
        private const val WIRE_TRANSLATION_VERSION_ID = "wiretranslations_version"
        private const val PERSONAL_TERMS_AND_CONDITIONS_SUFFIX = "/legal/terms/personal/"
        private const val TEAM_TERMS_AND_CONDITIONS_SUFFIX = "/legal/terms/teams/"
        private const val PRIVACY_POLICY_URL_SUFFIX = "/legal/privacy/embed/"
        private const val THIRD_PARTY_LICENSES_URL_SUFFIX = "/legal/#licenses"
    }
}

data class VersionDetails(
    @StringRes val avsVersionRes: Int,
    @StringRes val audioNotificationVersionRes: Int,
    val translationsVersionId: String,
    val appVersionDetails: String
)

data class AboutUrl(val url: String)
