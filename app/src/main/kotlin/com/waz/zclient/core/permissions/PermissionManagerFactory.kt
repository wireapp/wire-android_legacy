package com.waz.zclient.core.permissions

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class PermissionManagerFactory private constructor() {
    companion object {
        fun getPermissionManager(owner: AppCompatActivity) = ActivityPermissionManager(owner)
        fun getPermissionManager(owner: Fragment) = FragmentPermissionManager(owner)
    }
}
