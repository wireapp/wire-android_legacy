package com.waz.zclient.storage

import android.content.Context
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
open class IntegrationTest {
    protected fun getApplicationContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
}
