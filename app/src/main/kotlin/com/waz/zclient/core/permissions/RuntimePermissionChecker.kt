package com.waz.zclient.core.permissions

import android.os.Build
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.permissions.result.PermissionGranted
import com.waz.zclient.core.permissions.result.PermissionSuccess

interface RuntimePermissionChecker {
    fun requestPermissions(requester: PermissionRequester,
                           permissions: List<String>,
                           result: (Either<Failure, PermissionSuccess>) -> Unit)
}

class PostMarshmallowRuntimePermissionChecker : RuntimePermissionChecker {
    override fun requestPermissions(requester: PermissionRequester, permissions: List<String>, result: (Either<Failure, PermissionSuccess>) -> Unit) {
        //the "request" method from PermissionManager should go here
    }

}

class PreMarshmallowRuntimePermissionChecker : RuntimePermissionChecker {
    override fun requestPermissions(requester: PermissionRequester, permissions: List<String>, result: (Either<Failure, PermissionSuccess>) -> Unit) {
        //no need to do anything. automatically granted for pre Marshmallow
        result(Either.Right(PermissionGranted))
    }

}


class PermissionCheckerFactory {

    fun getPermissionChecker(): RuntimePermissionChecker {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PostMarshmallowRuntimePermissionChecker()
        else PreMarshmallowRuntimePermissionChecker()
    }
}
