package com.waz.zclient.core.navigation

import android.net.Uri
import android.os.Bundle
import androidx.annotation.IdRes

interface Navigator {
    fun navigateTo(@IdRes navigationId: Int)

    fun navigateTo(@IdRes navigationId: Int, bundle: Bundle)

    fun navigateTo(uri: Uri)
}
