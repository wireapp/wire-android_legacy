package com.waz.zclient.core.permissions.requesting

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.waz.zclient.core.permissions.PermissionRequester
import com.waz.zclient.utilities.device.SdkVersionChecker
import java.lang.ref.WeakReference

class PostMarshmallowPermissionRequest(val context: WeakReference<Activity>) : PermissionRequester {
    @RequiresApi(Build.VERSION_CODES.M)
    override fun invoke(p1: Array<out String>, p2: Int) {
        context.get()?.let { ActivityCompat.requestPermissions(it, p1, p2) }
    }
}

class PreMarshmallowPermissionRequest : PermissionRequester {
    override fun invoke(p1: Array<out String>, p2: Int) {
        // Pre marshmallow will behaves using the manifest
    }
}

class PermissionRequesterFactory private constructor() {
    companion object {
        fun getPermissionRequester(
            activity: Activity,
            sdkVersionChecker: SdkVersionChecker
        ): PermissionRequester =
            if (sdkVersionChecker.isAndroid6orAbove()) {
                PostMarshmallowPermissionRequest(WeakReference(activity))
            } else {
                PreMarshmallowPermissionRequest()
            }
    }
}
