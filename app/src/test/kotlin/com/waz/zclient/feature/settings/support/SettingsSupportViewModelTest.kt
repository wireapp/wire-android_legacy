package com.waz.zclient.feature.settings.support

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.livedata.observeOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Test

@ExperimentalCoroutinesApi
class SettingsSupportViewModelTest : UnitTest() {

    private var settingsSupportViewModel: SettingsSupportViewModel = SettingsSupportViewModel()

    @Test
    fun `given support button is clicked, then go to support website`() = runBlockingTest {
        settingsSupportViewModel.onSupportWebsiteClicked()

        settingsSupportViewModel.urlLiveData.observeOnce {
            it.url shouldBe SUPPORT_WEBSITE_URL
        }
    }

    @Test
    fun `given contact support button is clicked, then go to support website`() = runBlockingTest {
        settingsSupportViewModel.onSupportContactClicked()

        settingsSupportViewModel.urlLiveData.observeOnce {
            it.url shouldBe SUPPORT_CONTACT_URL
        }
    }

    companion object {
        private const val SUPPORT_WEBSITE_URL = "https://support.wire.com"
        private const val SUPPORT_CONTACT_URL = "${SUPPORT_WEBSITE_URL}/hc/requests/new"
    }
}
