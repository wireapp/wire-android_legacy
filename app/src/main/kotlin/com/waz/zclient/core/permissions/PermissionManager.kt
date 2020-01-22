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
import com.waz.zclient.core.permissions.handlers.PermissionHandler
import com.waz.zclient.core.permissions.handlers.StrictPermissionHandler
import com.waz.zclient.core.permissions.result.PermissionDenied
import com.waz.zclient.core.permissions.result.PermissionGranted
import com.waz.zclient.core.permissions.result.PermissionSuccess
import kotlin.math.abs

/**
 * Handles permissions across fragments and activities.
 * Credit to Michael Spitsin for the inspiration behind this mechanism.
 * https://medium.com/@programmerr47/working-with-permissions-in-android-bbba823be785
 */
typealias PermissionRequester = (Array<out String>, Int) -> Unit

typealias PermissionChecker = (String) -> Boolean

abstract class PermissionManager : LifecycleObserver {

    private val requestedPermissionHandlers = mutableMapOf<Int, PermissionHandler>()
    private val pendingPermissions = mutableMapOf<Int, RequestedPermissions>()

    internal lateinit var requester: PermissionRequester
    internal lateinit var checker: PermissionChecker

    protected fun isGranted(context: Context, permission: String): Boolean =
        ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun setup() {
        pendingPermissions.onEach { it.value.onPermissionResult() }
        pendingPermissions.clear()
    }

    //TODO still need to figure out how to unit test this
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestedPermissionHandlers.containsKey(requestCode)) {
            pendingPermissions[requestCode] = generateResult(requestCode, permissions, grantResults)
        }
    }

    private fun generateResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) =
        RequestedPermissions(requestedPermissionHandlers.remove(requestCode)!!, permissions, grantResults)

    fun strictPermissionRequest(permissions: List<String>, result: (Either<Failure, PermissionSuccess>) -> Unit) {
        request(permissions, StrictPermissionHandler(result), result)
    }

    fun lenientPermissionRequest(permissions: List<String>, result: (Either<Failure, PermissionSuccess>) -> Unit) {
        request(permissions, LenientPermissionHandler(result), result)
    }

    private fun request(
        permissions: List<String>,
        handler: PermissionHandler,
        result: (Either<Failure, PermissionSuccess>) -> Unit
    ) {
        val deniedPermissions = ArrayList(permissions.filterNot { checker(it) })
        val permissionArray = deniedPermissions.toTypedArray()
        if (permissionArray.isEmpty()) {
            result(Either.Right(PermissionGranted))
        } else {
            val id = abs(handler.hashCode().toShort().toInt())
            requestedPermissionHandlers[id] = handler
            requester(permissionArray, id)
        }
    }
}

private class RequestedPermissions internal constructor(
    private val permissionHandler: PermissionHandler,
    private val resultPermissions: Array<out String>,
    private val grantResults: IntArray
) {

    fun onPermissionResult() = permissionHandler.onPermissionResult(resultPermissions, grantResults)
}
