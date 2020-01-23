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
package com.waz.zclient.core.permissions.requesting

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.permissions.PermissionChecker
import com.waz.zclient.core.permissions.PermissionRequest
import com.waz.zclient.core.permissions.handlers.PermissionHandler
import com.waz.zclient.core.permissions.result.PermissionGranted
import com.waz.zclient.core.permissions.result.PermissionSuccess
import kotlin.math.abs

class PermissionRequester {

    private val requestedPermissionHandlers = mutableMapOf<Int, PermissionHandler>()

    fun request(
        permissionRequest: PermissionRequest,
        permissionChecker: PermissionChecker,
        permissions: List<String>,
        handler: PermissionHandler,
        result: (Either<Failure, PermissionSuccess>) -> Unit
    ) {
        val deniedPermissions = ArrayList(permissions.filterNot { permissionChecker(it) })
        val permissionArray = deniedPermissions.toTypedArray()
        if (permissionArray.isEmpty()) {
            result(Either.Right(PermissionGranted))
        } else {
            val id = abs(handler.hashCode().toShort().toInt())
            requestedPermissionHandlers[id] = handler
            permissionRequest(permissionArray, id)
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        permissionResult: (RequestedPermissions) -> Unit
    ) {
        if (requestedPermissionHandlers.containsKey(requestCode)) {
            permissionResult(generateResult(requestCode, permissions, grantResults))
        }
    }

    private fun generateResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) = RequestedPermissions(
        requestedPermissionHandlers.remove(requestCode)!!,
        permissions,
        grantResults
    )
}

class RequestedPermissions internal constructor(
    private val permissionHandler: PermissionHandler,
    private val resultPermissions: Array<out String>,
    private val grantResults: IntArray
) {
    fun onPermissionResult() = permissionHandler.onPermissionResult(resultPermissions, grantResults)
}
