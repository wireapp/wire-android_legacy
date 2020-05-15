package com.waz.zclient.feature.settings.support

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsSupportViewModel : ViewModel() {

    private val _urlLiveData = MutableLiveData<SupportUrl>()

    val urlLiveData = _urlLiveData

    fun onSupportWebsiteClicked() {
        _urlLiveData.value = SupportUrl(SUPPORT_WEBSITE_URL)
    }

    fun onSupportContactClicked() {
        _urlLiveData.value = SupportUrl(SUPPORT_CONTACT_URL)
    }

    companion object {
        private const val SUPPORT_WEBSITE_URL = "https://support.wire.com"
        private const val SUPPORT_CONTACT_URL = SUPPORT_WEBSITE_URL.plus("/hc/requests/new")
    }
}

data class SupportUrl(val url: String)
