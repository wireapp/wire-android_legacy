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
        // Pre marshmallow behaves using the manifest permissions
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
