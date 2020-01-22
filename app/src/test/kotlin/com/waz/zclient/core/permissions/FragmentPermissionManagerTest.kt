package com.waz.zclient.core.permissions

import android.Manifest
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.permissions.result.PermissionDenied
import com.waz.zclient.core.permissions.result.PermissionGranted
import com.waz.zclient.utilities.device.SdkVersionChecker
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class FragmentPermissionManagerTest : UnitTest() {

    private lateinit var permissionManager: PermissionManager

    @Mock
    private lateinit var fragment: Fragment

    @Mock
    private lateinit var activity: FragmentActivity

    @Mock
    private lateinit var sdkChecker: SdkVersionChecker

    @Mock
    private lateinit var permissionChecker: PermissionChecker

    @Mock
    private lateinit var permissionRequester: PermissionRequester

    @Before
    fun setup() {
        `when`(sdkChecker.isAndroid6orAbove()).thenReturn(true)
        val lifecycle = LifecycleRegistry(Mockito.mock(LifecycleOwner::class.java))
        `when`(fragment.lifecycle).thenReturn(lifecycle)
        `when`(fragment.requireActivity()).thenReturn(activity)

        permissionManager = FragmentPermissionManager(fragment, sdkChecker)
    }

    @Test
    fun `given CAMERA permission is requested strictly, when CAMERA permission is already granted, then return PermissionGranted `() {
        permissionManager.checker = permissionChecker
        permissionManager.requester = permissionRequester

        `when`(permissionChecker(Manifest.permission.CAMERA)).thenReturn(true)

        permissionManager.strictPermissionRequest(listOf(Manifest.permission.CAMERA)) { either ->
            either.onSuccess {
                it shouldBe PermissionGranted
            }
        }
    }

    @Test
    fun `given multiple permissions is requested strictly, when one permission is denied, then return PermissionDenied `() {
        permissionManager.checker = permissionChecker
        permissionManager.requester = permissionRequester

        `when`(permissionChecker(Manifest.permission.RECORD_AUDIO)).thenReturn(false)
        `when`(permissionChecker(Manifest.permission.CAMERA)).thenReturn(true)

        val listOfPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        permissionManager.strictPermissionRequest(listOfPermissions) { either ->
            either.onFailure {
                it shouldBe PermissionDenied(listOf(Manifest.permission.RECORD_AUDIO))
            }
        }
    }
}
