package com.waz.zclient.core.permissions

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.zclient.utilities.device.SdkVersionChecker

class PermissionManagerFactory(private val sdkVersionChecker: SdkVersionChecker) {

    fun getPermissionManager(activity: AppCompatActivity): PermissionManager =
        ActivityPermissionManager(activity, sdkVersionChecker)

    fun getPermissionManager(fragment: Fragment): PermissionManager =
        FragmentPermissionManager(fragment, sdkVersionChecker)
}
