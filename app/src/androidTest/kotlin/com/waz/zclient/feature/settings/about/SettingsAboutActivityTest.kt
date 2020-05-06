package com.waz.zclient.feature.settings.about

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.waz.zclient.FunctionalTest
import com.waz.zclient.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test

@ExperimentalCoroutinesApi
class SettingsAboutActivityTest : FunctionalTest(SettingsAboutActivity::class.java) {

    @Test
    fun checkToolbar() {
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
    }
}
