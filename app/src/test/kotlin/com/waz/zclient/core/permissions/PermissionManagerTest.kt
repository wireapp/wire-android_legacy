package com.waz.zclient.core.permissions

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.permissions.extension.readPhoneState
import com.waz.zclient.core.permissions.result.PermissionDenied
import com.waz.zclient.core.permissions.result.PermissionGranted
import com.waz.zclient.utilities.device.SdkVersionChecker
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class PermissionManagerTest : UnitTest() {

    private lateinit var permissionManager: PermissionManager

    @Mock
    private lateinit var activity: AppCompatActivity

    @Mock
    private lateinit var fragment: Fragment

    @Mock
    private lateinit var sdkChecker: SdkVersionChecker

    @Mock
    private lateinit var permissionChecker: PermissionChecker

    @Mock
    private lateinit var permissionRequester: PermissionRequester

    @Before
    fun setup() {
        `when`(sdkChecker.isAndroid6orAbove()).thenReturn(true)
        permissionManager = PermissionManager(sdkChecker)
        permissionManager.from(fragment)
    }

    @Test
    fun `given CAMERA permission is requested strictly from activity, when CAMERA permission is already granted, then return PermissionGranted `() {
        permissionManager.from(activity)

        permissionManager.checker = permissionChecker
        permissionManager.requester = permissionRequester

        `when`(permissionChecker(Manifest.permission.CAMERA)).thenReturn(true)

        permissionManager.requestPermissions(listOf(Manifest.permission.CAMERA)) { either ->
            either.onSuccess {
                it shouldBe PermissionGranted
            }
        }
    }

    @Test
    fun `given multiple permissions is requested strictly from activity, when one permission is denied, then return PermissionDenied `() {
        permissionManager.from(activity)

        permissionManager.checker = permissionChecker
        permissionManager.requester = permissionRequester

        `when`(permissionChecker(Manifest.permission.RECORD_AUDIO)).thenReturn(false)
        `when`(permissionChecker(Manifest.permission.CAMERA)).thenReturn(true)

        val listOfPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        permissionManager.requestPermissions(listOfPermissions) { either ->
            either.onFailure {
                it shouldBe PermissionDenied(listOf(Manifest.permission.RECORD_AUDIO))
            }
        }
    }

    @Test
    fun `given CAMERA permission is requested strictly from fragment, when CAMERA permission is already granted, then return PermissionGranted `() {
        permissionManager.checker = permissionChecker
        permissionManager.requester = permissionRequester

        `when`(permissionChecker(Manifest.permission.CAMERA)).thenReturn(true)

        permissionManager.requestPermissions(listOf(Manifest.permission.CAMERA)) { either ->
            either.onSuccess {
                it shouldBe PermissionGranted
            }
        }
    }

    @Test
    fun `given multiple permissions is requested strictly from fragment, when one permission is denied, then return PermissionDenied `() {
        permissionManager.checker = permissionChecker
        permissionManager.requester = permissionRequester

        `when`(permissionChecker(Manifest.permission.RECORD_AUDIO)).thenReturn(false)
        `when`(permissionChecker(Manifest.permission.CAMERA)).thenReturn(true)

        val listOfPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        permissionManager.requestPermissions(listOfPermissions) { either ->
            either.onFailure {
                it shouldBe PermissionDenied(listOf(Manifest.permission.RECORD_AUDIO))
            }
        }
    }


    @Test
    fun `given readPhoneState extension method is called, when permission is granted, then return PermissionGranted`() {
        permissionManager.checker = permissionChecker
        permissionManager.requester = permissionRequester

        `when`(permissionChecker(Manifest.permission.READ_PHONE_STATE)).thenReturn(true)

        permissionManager.readPhoneState { either ->
            either.onSuccess {
                it shouldBe PermissionGranted
            }
        }
    }

    @Test
    fun `given readPhoneState extension method is called, when permission is granted, then return PermissionDenied`() {
        permissionManager.checker = permissionChecker
        permissionManager.requester = permissionRequester

        `when`(permissionChecker(Manifest.permission.READ_PHONE_STATE)).thenReturn(false)

        permissionManager.readPhoneState { either ->
            either.onFailure {
                it shouldBe PermissionDenied(listOf(Manifest.permission.READ_PHONE_STATE))
            }
        }
    }

}
