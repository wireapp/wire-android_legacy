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

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.ActivityLifecycleObserver
import com.waz.zclient.core.extension.FragmentLifecycleObserver
import com.waz.zclient.core.extension.addObserver
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.permissions.handlers.PermissionHandler
import com.waz.zclient.core.permissions.handlers.StrictPermissionHandler
import com.waz.zclient.core.permissions.result.PermissionGranted
import com.waz.zclient.core.permissions.result.PermissionSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Handles permissions across fragments and activities.
 * Credit to Michael Spitsin for the inspiration around the permissions.
 * https://medium.com/@programmerr47/working-with-permissions-in-android-bbba823be785
 */

typealias PermissionRequester = (Array<String>, Int) -> Unit

typealias PermissionChecker = (String) -> Boolean

class PermissionManager : FragmentLifecycleObserver, ActivityLifecycleObserver {

    private val requestedPermissionHandlers = mutableMapOf<Int, PermissionHandler>()
    private val pendingPermissions = mutableMapOf<Int, RequestedPermissions>()

    private lateinit var requester: PermissionRequester
    private lateinit var checker: PermissionChecker

    override fun from(owner: Fragment) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requester = owner::requestPermissions
            checker = { owner.context?.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        }
    }

    override fun from(owner: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requester = owner::requestPermissions
            checker = { owner.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun setup() {
        pendingPermissions.onEach { it.value.onPermissionResult() }
        pendingPermissions.clear()
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestedPermissionHandlers.containsKey(requestCode)) {
            pendingPermissions[requestCode] = RequestedPermissions(requestedPermissionHandlers.remove(requestCode)!!, permissions, grantResults)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun requestPermissions(permissions: List<String>, result: (Either<Failure, PermissionSuccess>) -> Unit) = withContext(Dispatchers.Main) {
        request(permissions, StrictPermissionHandler(result), result)
    }

    private fun request(permissions: List<String>, handler: PermissionHandler, result: (Either<Failure, PermissionSuccess>) -> Unit) {
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

    companion object {

        fun newInstance(owner: AppCompatActivity): PermissionManager =
            PermissionManager().also {
                owner.lifecycle.addObserver(it, owner)
                it.from(owner)
            }

        fun newInstance(owner: Fragment): PermissionManager =
            PermissionManager().also {
                owner.lifecycle.addObserver(it, owner)
                it.from(owner)
            }
    }
}

private class RequestedPermissions internal constructor(
    private val permissionHandler: PermissionHandler,
    private val resultPermissions: Array<out String>,
    private val grantResults: IntArray) {

    fun onPermissionResult() = permissionHandler.onPermissionResult(resultPermissions, grantResults)
}
