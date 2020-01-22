package com.waz.zclient.core.permissions.handlers

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.permissions.result.PermissionContinued
import com.waz.zclient.core.permissions.result.PermissionDenied
import com.waz.zclient.core.permissions.result.PermissionSuccess

class LenientPermissionHandler(private val onResult: (Either<Failure, PermissionSuccess>) -> Unit) :
    PermissionHandler {

    override fun onPermissionResult(permissions: Array<out String>, grantResults: IntArray) {
        calculateResult(permissions, grantResults)
    }

    private fun calculateResult(
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val denied = deniedPermissions(grantResults)
        if (denied.isNotEmpty()) {
            onResult(Either.Left(PermissionDenied(denied.map { permissions[it] })))
        }

        onResult(Either.Right(PermissionContinued))
    }
}
