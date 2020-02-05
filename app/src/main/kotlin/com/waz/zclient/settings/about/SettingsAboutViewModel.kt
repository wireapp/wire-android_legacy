package com.waz.zclient.settings.about

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsAboutViewModel(
    private val appDetailsConfig: AppDetailsConfig,
    private val urlConfig: UrlConfig,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    private var _urlLiveData = MutableLiveData<UrlDetail>()
    private var _versionDetailsLiveData = MutableLiveData<VersionDetails>()

    val urlLiveData: LiveData<UrlDetail> = _urlLiveData
    val versionDetailsLiveData: LiveData<VersionDetails> = _versionDetailsLiveData

    fun onAboutButtonClicked() {
        _urlLiveData.value = UrlDetail(generateUrl(String.empty()))
    }

    fun onPrivacyButtonClicked() {
        _urlLiveData.value = UrlDetail(generateUrl(PRIVACY_POLICY_URL_SUFFIX))
    }

    fun onThirdPartyLicenseButtonClicked() {
        _urlLiveData.value = UrlDetail(generateUrl(THIRD_PARTY_LICENSES_URL_SUFFIX))
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
                appDetailsConfig.version
            )
        }
    }

    private fun updateProfileData(user: User) {
        _urlLiveData.value = if (user.teamId.isNullOrEmpty()) {
            UrlDetail(generateUrl(PERSONAL_TERMS_AND_CONDITIONS_SUFFIX))
        } else UrlDetail(generateUrl(TEAM_TERMS_AND_CONDITIONS_SUFFIX))
    }

    private fun generateUrl(urlSuffix: String): String =
        if (urlSuffix.isEmpty()) urlConfig.configUrl
        else urlConfig.configUrl.plus(urlSuffix)

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

data class AppDetailsConfig(val version: String)
data class UrlConfig(val configUrl: String)
data class UrlDetail(val url: String)
