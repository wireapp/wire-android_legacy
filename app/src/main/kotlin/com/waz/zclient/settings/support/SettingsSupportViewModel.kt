package com.waz.zclient.settings.support

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsSupportViewModel : ViewModel() {

    private var _urlLiveData = MutableLiveData<UrlDetail>()

    val urlLiveData = _urlLiveData

    fun onSupportWebsiteClicked() {
        _urlLiveData.value = UrlDetail(SUPPORT_WEBSITE_URL)
    }

    fun onSupportContactClicked() {
        _urlLiveData.value = UrlDetail(SUPPORT_CONTACT_URL)
    }

    companion object {
        private const val SUPPORT_WEBSITE_URL = "https://support.wire.com"
        private const val SUPPORT_CONTACT_URL = SUPPORT_WEBSITE_URL.plus("/hc/requests/new")
    }
}

data class UrlDetail(val url: String)
