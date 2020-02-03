package com.waz.zclient.settings.about

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.extension.empty
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsAboutViewModel(
    private val urlConfig: UrlConfig,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    private var _urlLiveData = MutableLiveData<UrlDetail>()

    val urlLiveData: LiveData<UrlDetail> = _urlLiveData

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

    private fun updateProfileData(user: User) {
        _urlLiveData.value = if (user.teamId.isNullOrEmpty()) {
            UrlDetail(generateUrl(PERSONAL_TERMS_AND_CONDITIONS_SUFFIX))
        } else UrlDetail(generateUrl(TEAM_TERMS_AND_CONDITIONS_SUFFIX))
    }

    private fun generateUrl(urlSuffix: String): String =
        if (urlSuffix.isEmpty()) urlConfig.configUrl
        else urlConfig.configUrl.plus(urlSuffix)

    companion object {
        private const val PERSONAL_TERMS_AND_CONDITIONS_SUFFIX = "/legal/terms/personal/"
        private const val TEAM_TERMS_AND_CONDITIONS_SUFFIX = "/legal/terms/teams/"
        private const val PRIVACY_POLICY_URL_SUFFIX = "/legal/privacy/embed/"
        private const val THIRD_PARTY_LICENSES_URL_SUFFIX = "/legal/#licenses"
    }
}

data class UrlConfig(val configUrl: String)
data class UrlDetail(val url: String)
