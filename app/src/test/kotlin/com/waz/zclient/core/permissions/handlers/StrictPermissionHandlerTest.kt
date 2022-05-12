package com.waz.zclient.core.permissions.handlers

import android.content.pm.PackageManager
import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.permissions.result.PermissionDenied
import com.waz.zclient.core.permissions.result.PermissionGranted
import org.amshove.kluent.shouldBe
import org.junit.Test

class StrictPermissionHandlerTest : UnitTest() {

    private lateinit var permissionHandler: PermissionHandler

    @Test
    fun `given permission requested, when permission is granted, then return PermissionGranted`() {
        val permissions = arrayOf(android.Manifest.permission.CAMERA)
        val grantResults = intArrayOf(PackageManager.PERMISSION_GRANTED)

        permissionHandler = StrictPermissionHandler {
            it.onSuccess { success ->
                success shouldBe PermissionGranted
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)
    }

    @Test
    fun `given list of permissions requested, when all permissions are granted, then return PermissionGranted`() {
        val permissions = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
        val grantResults = intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED)

        permissionHandler = StrictPermissionHandler {
            it.onSuccess { success ->
                success shouldBe PermissionGranted
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)

    }


    @Test
    fun `given CAMERA permission is requested, when permission is denied, then return PermissionDenied`() {
        val permissions = arrayOf(android.Manifest.permission.CAMERA)
        val grantResults = intArrayOf(PackageManager.PERMISSION_DENIED)

        permissionHandler = StrictPermissionHandler {
            it.onFailure { failure ->
                //TODO should be doesn't work right now. Will find out issue and resolve here.
                //failure shouldBe PermissionDenied(listOf(android.Manifest.permission.CAMERA))
                assert(failure == PermissionDenied(listOf(android.Manifest.permission.CAMERA)))
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)

    }

    @Test
    fun `given list of permissions requested, when one permission is granted, one denied, then return PermissionDenied`() {
        val permissions = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
        val grantResults = intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED)

        permissionHandler = StrictPermissionHandler {
            it.onFailure { failure ->
                assert(failure == PermissionDenied(listOf(android.Manifest.permission.CAMERA)))
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)

    }

    @Test
    fun `given list of permissions requested, when all permissions are denied, then return PermissionDenied`() {
        val permissions = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
        val grantResults = intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_DENIED)

        permissionHandler = StrictPermissionHandler {
            it.onFailure { failure ->
                assert(failure == PermissionDenied(listOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)))
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)
    }
}
