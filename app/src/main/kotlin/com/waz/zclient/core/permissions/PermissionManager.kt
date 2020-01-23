/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.permissions.handlers.LenientPermissionHandler
import com.waz.zclient.core.permissions.handlers.StrictPermissionHandler
import com.waz.zclient.core.permissions.requesting.PermissionRequester
import com.waz.zclient.core.permissions.requesting.RequestedPermissions
import com.waz.zclient.core.permissions.result.PermissionSuccess

/**
 * Handles permissions across fragments and activities.
 * Credit to Michael Spitsin for the inspiration behind this mechanism.
 * https://medium.com/@programmerr47/working-with-permissions-in-android-bbba823be785
 */
typealias PermissionChecker = (String) -> Boolean

typealias PermissionRequest = (Array<out String>, Int) -> Unit

abstract class PermissionManager : LifecycleObserver {

    private val pendingPermissions = mutableMapOf<Int, RequestedPermissions>()

    private val permissionRequester: PermissionRequester by lazy {
        PermissionRequester()
    }

    internal lateinit var request: PermissionRequest
    internal lateinit var checker: PermissionChecker

    protected fun isGranted(context: Context, permission: String): Boolean =
        ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun updatePendingPermissions() {
        pendingPermissions.onEach { it.value.onPermissionResult() }
        pendingPermissions.clear()
    }

    //TODO still need to figure out how to unit test this
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permissionRequester.onRequestPermissionsResult(requestCode, permissions, grantResults) {
            pendingPermissions[requestCode] = it
        }
    }

    fun strictPermissionRequest(permissions: List<String>, result: (Either<Failure, PermissionSuccess>) -> Unit) {
        permissionRequester.request(request, checker, permissions, StrictPermissionHandler(result), result)
    }

    fun lenientPermissionRequest(permissions: List<String>, result: (Either<Failure, PermissionSuccess>) -> Unit) {
        permissionRequester.request(request, checker, permissions, LenientPermissionHandler(result), result)
    }
}
