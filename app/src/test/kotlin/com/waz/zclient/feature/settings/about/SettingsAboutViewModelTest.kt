package com.waz.zclient.feature.settings.about

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.core.config.AppDetailsConfig
import com.waz.zclient.core.config.HostUrlConfig
import com.waz.zclient.core.extension.empty
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.profile.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.lenient

@ExperimentalCoroutinesApi
class SettingsAboutViewModelTest : UnitTest() {

    private lateinit var settingsAboutViewModel: SettingsAboutViewModel

    @Mock
    private lateinit var hostUrl: HostUrlConfig

    @Mock
    private lateinit var versionDetailsConfig: AppDetailsConfig

    @Mock
    private lateinit var profileUseCase: GetUserProfileUseCase

    @Mock
    private lateinit var user: User

    private lateinit var userFlow: Flow<User>

    @Before
    fun setup() {
        settingsAboutViewModel = SettingsAboutViewModel(versionDetailsConfig, hostUrl, profileUseCase)
        Mockito.`when`(hostUrl.url).thenReturn(CONFIG_URL)
        userFlow = flow { user }
    }

    @Test
    fun `given terms button is clicked, when team id exists, then provide team terms and conditions url`() = runBlockingTest {
        lenient().`when`(user.teamId).thenReturn(TEST_TEAM_ID)
        lenient().`when`(profileUseCase.run(Unit)).thenReturn(userFlow)

        settingsAboutViewModel.onTermsButtonClicked()

        userFlow.collect {
            settingsAboutViewModel.urlLiveData.observeOnce {
                it.url shouldBe TEAM_TERMS_AND_CONDITIONS_TEST_URL
            }
        }
    }

    @Test
    fun `given terms button is clicked, when team id is empty, then open personal terms and conditions url`() = runBlockingTest {
        lenient().`when`(user.teamId).thenReturn(String.empty())
        lenient().`when`(profileUseCase.run(Unit)).thenReturn(userFlow)

        settingsAboutViewModel.onTermsButtonClicked()

        userFlow.collect {
            settingsAboutViewModel.urlLiveData.observeOnce {
                it.url shouldBe PERSONAL_TERMS_AND_CONDITIONS_TEST_URL
            }
        }
    }

    @Test
    fun `given about button is clicked, then open config url`() {
        settingsAboutViewModel.onAboutButtonClicked()

        settingsAboutViewModel.urlLiveData.observeOnce {
            it.url shouldBe CONFIG_URL
        }
    }

    @Test
    fun `given privacy button is clicked, then open privacy url`() {
        settingsAboutViewModel.onPrivacyButtonClicked()

        settingsAboutViewModel.urlLiveData.observeOnce {
            assert(it.url == PRIVACY_POLICY_TEST_URL)
        }
    }

    @Test
    fun `given third party licenses button is clicked, then third party licenses url`() {
        settingsAboutViewModel.onThirdPartyLicenseButtonClicked()

        settingsAboutViewModel.urlLiveData.observeOnce {
            assert(it.url == THIRD_PARTY_LICENSES_TEST_URL)
        }
    }

    @Test
    fun `given version button is clicked over 10 times, then show version details`() {
        Mockito.`when`(versionDetailsConfig.versionDetails).thenReturn(TEST_VERSION)

        for (i in 1..11) {
            settingsAboutViewModel.onVersionButtonClicked()
        }
        settingsAboutViewModel.versionDetailsLiveData.observeOnce {
            it.appVersionDetails shouldBe TEST_VERSION
            assert(it.audioNotificationVersionRes == R.string.audio_notifications_version)
            assert(it.avsVersionRes == R.string.avs_version)
            it.translationsVersionId shouldBe WIRE_TRANSLATION_VERSION_ID
        }
    }

    companion object {
        private const val TEST_VERSION = "version"
        private const val WIRE_TRANSLATION_VERSION_ID = "wiretranslations_version"
        private const val TEST_TEAM_ID = "teamId"
        private const val CONFIG_URL = "http://wire.com"
        private const val PERSONAL_TERMS_AND_CONDITIONS_TEST_URL = "${CONFIG_URL}/legal/terms/personal/"
        private const val TEAM_TERMS_AND_CONDITIONS_TEST_URL = "${CONFIG_URL}/legal/terms/teams/"
        private const val PRIVACY_POLICY_TEST_URL = "${CONFIG_URL}/legal/privacy/embed/"
        private const val THIRD_PARTY_LICENSES_TEST_URL = "${CONFIG_URL}/legal/#licenses"
    }
}
