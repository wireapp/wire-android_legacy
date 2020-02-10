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
package com.waz.zclient.core.permissions.handlers

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.permissions.result.PermissionContinued
import com.waz.zclient.core.permissions.result.PermissionDenied
import com.waz.zclient.core.permissions.result.PermissionSuccess

/**
 * This is the LENIENT permission handler because even if the user denies the permission,
 * you can continue down the happy path
 */
class LenientPermissionHandler(private val onResult: (Either<Failure, PermissionSuccess>) -> Unit) :
    PermissionHandler {

    override fun onPermissionResult(permissions: Array<out String>, grantResults: IntArray) {
        generateResult(permissions, grantResults)
    }

    private fun generateResult(permissions: Array<out String>, grantResults: IntArray) {
        val denied = deniedPermissions(grantResults)
        if (denied.isNotEmpty()) {
            onResult(Either.Left(PermissionDenied(denied.map { permissions[it] })))
        }
        onResult(Either.Right(PermissionContinued))
    }
}
