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

import androidx.core.content.PermissionChecker
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.permissions.result.PermissionDenied
import com.waz.zclient.core.permissions.result.PermissionGranted
import com.waz.zclient.core.permissions.result.PermissionSuccess

/**
 * This is the STRICT permission handler because you won't be able to continue down the "happy" path
 * if you deny the permission. More handlers can be added for more lenient paths you'd want the permissions to take.
 */
class StrictPermissionHandler(private val onResult: (Either<Failure, PermissionSuccess>) -> Unit) :
    PermissionHandler {

    override fun onPermissionResult(permissions: Array<out String>, grantResults: IntArray) {
        onResult(calculateResult(permissions, grantResults))
    }

    private fun calculateResult(
        permissions: Array<out String>,
        grantResults: IntArray
    ): Either<Failure, PermissionSuccess> {
        val denied = deniedPermissions(grantResults)
        return if (denied.isEmpty()) {
            Either.Right(PermissionGranted)
        } else {
            Either.Left(PermissionDenied(denied.map { permissions[it] }))
        }
    }
}
