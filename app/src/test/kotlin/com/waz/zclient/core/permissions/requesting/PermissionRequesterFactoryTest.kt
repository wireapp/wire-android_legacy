package com.waz.zclient.core.permissions.requesting

import android.app.Activity
import com.waz.zclient.UnitTest
import com.waz.zclient.utilities.device.SdkVersionChecker
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito

class PermissionRequesterFactoryTest : UnitTest() {

    @Mock
    private lateinit var activity: Activity

    @Mock
    private lateinit var sdkChecker: SdkVersionChecker

    @Test
    fun `test if android android is 6 or above, request should be PostMarshmallowRequester`() {
        Mockito.`when`(sdkChecker.isAndroid6orAbove()).thenReturn(true)

        val request = PermissionRequesterFactory.getPermissionRequester(activity, sdkChecker)

        request shouldBeInstanceOf PostMarshmallowPermissionRequest::class.java
    }

    @Test
    fun `test if android android is 6 or above, request should be PreMarshmallowRequester`() {
        Mockito.`when`(sdkChecker.isAndroid6orAbove()).thenReturn(false)

        val request = PermissionRequesterFactory.getPermissionRequester(activity, sdkChecker)

        request shouldBeInstanceOf PreMarshmallowPermissionRequest::class.java

    }
}
