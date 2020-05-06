package com.waz.zclient

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
abstract class FunctionalTest(clazz: Class<*>) {

    @get:Rule
    var activityRule = activityTestRule(clazz)

    private fun activityTestRule(activityClass: Class<*>): ActivityTestRule<out Activity> {
        require(activityClass is Activity) { "Wrong class type: Use Android Activity type." }
        return ActivityTestRule(activityClass.asSubclass(Activity::class.java))
    }
}
