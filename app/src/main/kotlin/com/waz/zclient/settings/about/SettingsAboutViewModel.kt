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
class SettingsAboutViewModel(private val getUserProfileUseCase: GetUserProfileUseCase)
    : ViewModel() {

    private var _urlLiveData = MutableLiveData<UrlDetail>()

    val urlLiveData: LiveData<UrlDetail> = _urlLiveData

    fun onAboutButtonClicked() {
        _urlLiveData.value = UrlDetail.EMPTY
    }

    fun onPrivacyButtonClicked() {
        _urlLiveData.value = UrlDetail(PRIVACY_POLICY_URL_SUFFIX)
    }

    fun onThirdPartyLicenseButtonClicked() {
        _urlLiveData.value = UrlDetail(THIRD_PARTY_LICENSES_URL_SUFFIX)
    }

    fun onTermsButtonClicked() {
        getUserProfileUseCase(viewModelScope, Unit) {
            it.fold({}, ::updateProfileData)
        }
    }

    private fun updateProfileData(user: User) {
        if (user.teamId.isNullOrEmpty()) {
            _urlLiveData.value = UrlDetail(PERSONAL_TERMS_AND_CONDITIONS_SUFFIX)
        } else {
            _urlLiveData.value = UrlDetail(TEAM_TERMS_AND_CONDITIONS_SUFFIX)
        }
    }

    companion object {
        private const val PERSONAL_TERMS_AND_CONDITIONS_SUFFIX = "/legal/terms/personal/"
        private const val TEAM_TERMS_AND_CONDITIONS_SUFFIX = "/legal/terms/teams/"
        private const val PRIVACY_POLICY_URL_SUFFIX = "/legal/privacy/embed/"
        private const val THIRD_PARTY_LICENSES_URL_SUFFIX = "/legal/#licenses"
    }
}

data class UrlDetail(val urlSuffix: String) {
    companion object {
        val EMPTY = UrlDetail(String.empty())
    }
}
