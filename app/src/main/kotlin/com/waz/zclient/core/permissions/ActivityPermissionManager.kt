package com.waz.zclient.core.permissions

import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.core.permissions.requesting.PermissionRequesterFactory
import com.waz.zclient.utilities.device.SdkVersionChecker
import java.lang.ref.WeakReference

class ActivityPermissionManager(activity: AppCompatActivity,
                                sdkVersionChecker: SdkVersionChecker = SdkVersionChecker()
) : PermissionManager() {

    private val activityRef = WeakReference<AppCompatActivity>(activity)

    init {
        activityRef.get()?.let {
            it.lifecycle.addObserver(this)
            requester = PermissionRequesterFactory.getPermissionRequester(it, sdkVersionChecker)
            checker = { permission -> isGranted(it, permission) }
        }
    }
}
