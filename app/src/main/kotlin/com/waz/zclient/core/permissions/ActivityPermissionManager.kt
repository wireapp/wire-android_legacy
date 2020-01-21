package com.waz.zclient.core.permissions

import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.utilities.device.SdkVersionChecker
import java.lang.ref.WeakReference

class ActivityPermissionManager(
    activity: AppCompatActivity,
    override val sdkChecker: SdkVersionChecker
): PermissionManager() {

    private val activityRef = WeakReference<AppCompatActivity>(activity)

    init {
        activityRef.get()?.let{
            it.lifecycle.addObserver(this)
            requester = it::requestPermissions
            checker = { permChecker -> isGranted(permChecker, it) }
        }
    }
}
