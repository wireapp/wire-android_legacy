package com.waz.zclient.core.permissions.handlers

import android.Manifest
import android.content.pm.PackageManager
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.permissions.result.PermissionContinued
import com.waz.zclient.core.permissions.result.PermissionDenied
import org.amshove.kluent.shouldBe
import org.junit.Test

class LenientPermissionHandlerTest {

    private lateinit var permissionHandler: PermissionHandler

    @Test
    fun `given permission requested, when permission is granted, then return PermissionContinued`() {
        val permissions = arrayOf(Manifest.permission.CAMERA)
        val grantResults = intArrayOf(PackageManager.PERMISSION_GRANTED)

        permissionHandler = LenientPermissionHandler {
            it.onSuccess { success ->
                success shouldBe PermissionContinued
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)
    }

    @Test
    fun `given list of permissions requested, when all permissions are granted, then return PermissionContinued`() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val grantResults = intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED)

        permissionHandler = LenientPermissionHandler {
            it.onSuccess { success ->
                success shouldBe PermissionContinued
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)

    }


    @Test
    fun `given CAMERA permission is requested, when permission is denied, then return PermissionDenied and PermissionContinued`() {
        val permissions = arrayOf(Manifest.permission.CAMERA)
        val grantResults = intArrayOf(PackageManager.PERMISSION_DENIED)

        permissionHandler = LenientPermissionHandler {
            it.onSuccess { success ->
                assert(success == PermissionContinued)
            }
            it.onFailure { failure ->
                //TODO should be doesn't work right now. Will find out issue and resolve here.
                //failure shouldBe PermissionDenied(listOf(android.Manifest.permission.CAMERA))
                assert(failure == PermissionDenied(listOf(Manifest.permission.CAMERA)))
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)

    }

    @Test
    fun `given list of permissions requested, when one permission is granted, one denied, then return PermissionDenied and PermissionContinued`() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val grantResults = intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED)

        permissionHandler = LenientPermissionHandler {
            it.onSuccess { success ->
                assert(success == PermissionContinued)
            }
            it.onFailure { failure ->
                assert(failure == PermissionDenied(listOf(Manifest.permission.CAMERA)))
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)

    }

    @Test
    fun `given list of permissions requested, when all permissions are denied, then return PermissionDenied and PermissionContinued`() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val grantResults = intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_DENIED)

        permissionHandler = LenientPermissionHandler {
            it.onSuccess { success ->
                assert(success == PermissionContinued)
            }
            it.onFailure { failure ->
                assert(failure == PermissionDenied(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)))
            }
        }

        permissionHandler.onPermissionResult(permissions, grantResults)
    }
}
