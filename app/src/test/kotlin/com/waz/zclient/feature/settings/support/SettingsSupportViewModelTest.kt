package com.waz.zclient.feature.settings.support

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.livedata.awaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class SettingsSupportViewModelTest : UnitTest() {

    private var settingsSupportViewModel: SettingsSupportViewModel = SettingsSupportViewModel()

    @Test
    fun `given support button is clicked, then go to support website`() = runBlocking {
        settingsSupportViewModel.onSupportWebsiteClicked()

        assertEquals(SUPPORT_WEBSITE_URL, settingsSupportViewModel.urlLiveData.awaitValue().url)
    }

    @Test
    fun `given contact support button is clicked, then go to support website`() = runBlocking {
        settingsSupportViewModel.onSupportContactClicked()

        assertEquals(SUPPORT_CONTACT_URL, settingsSupportViewModel.urlLiveData.awaitValue().url)
    }

    companion object {
        private const val SUPPORT_WEBSITE_URL = "https://support.wire.com"
        private const val SUPPORT_CONTACT_URL = "${SUPPORT_WEBSITE_URL}/hc/requests/new"
    }
}
