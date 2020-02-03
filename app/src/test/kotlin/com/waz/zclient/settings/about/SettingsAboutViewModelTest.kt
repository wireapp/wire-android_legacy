package com.waz.zclient.settings.about

import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
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

@ExperimentalCoroutinesApi
class SettingsAboutViewModelTest : UnitTest() {

    private lateinit var settingsAboutViewModel: SettingsAboutViewModel

    @Mock
    private lateinit var urlConfig: UrlConfig

    @Mock
    private lateinit var profileUseCase: GetUserProfileUseCase

    private lateinit var userFlow: Flow<User>

    @Mock
    private lateinit var user: User

    @Before
    fun setup() {
        settingsAboutViewModel = SettingsAboutViewModel(urlConfig, profileUseCase)
        Mockito.`when`(urlConfig.configUrl).thenReturn(CONFIG_URL)
        userFlow = flow { user }

    }

    @Test
    fun `given terms button is clicked, when team id exists, then provide team terms and conditions url`() = runBlockingTest {
        Mockito.`when`(user.teamId).thenReturn(TEST_TEAM_ID)
        Mockito.`when`(profileUseCase.run(Unit)).thenReturn(userFlow)

        settingsAboutViewModel.onTermsButtonClicked()

        userFlow.collect {
            settingsAboutViewModel.urlLiveData.observeOnce {
                it.url shouldBe TEAM_TERMS_AND_CONDITIONS_TEST_URL
            }
        }
    }

    @Test
    fun `given terms button is clicked, when team id is empty, then open personal terms and conditions url`() = runBlockingTest {
        Mockito.`when`(user.teamId).thenReturn(String.empty())
        Mockito.`when`(profileUseCase.run(Unit)).thenReturn(userFlow)

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

        settingsAboutViewModel.urlLiveData.observeForever {
            it.url shouldBe CONFIG_URL
        }
    }

    @Test
    fun `given privacy button is clicked, then open privacy url`() {
        settingsAboutViewModel.onPrivacyButtonClicked()

        settingsAboutViewModel.urlLiveData.observeForever {
            it.url shouldBe PRIVACY_POLICY_TEST_URL
        }
    }

    @Test
    fun `given third party licenses button is clicked, then third party licenses url`() {
        settingsAboutViewModel.onThirdPartyLicenseButtonClicked()

        settingsAboutViewModel.urlLiveData.observeForever {
            it.url shouldBe THIRD_PARTY_LICENSES_TEST_URL
        }
    }

    companion object {
        private const val TEST_TEAM_ID = "teamId"
        private const val CONFIG_URL = "http://wire.com"
        private const val PERSONAL_TERMS_AND_CONDITIONS_TEST_URL = "${CONFIG_URL}/legal/terms/personal/"
        private const val TEAM_TERMS_AND_CONDITIONS_TEST_URL = "${CONFIG_URL}/legal/terms/teams/"
        private const val PRIVACY_POLICY_TEST_URL = "${CONFIG_URL}/legal/privacy/embed/"
        private const val THIRD_PARTY_LICENSES_TEST_URL = "${CONFIG_URL}/legal/#licenses"
    }
}
