package com.waz.zclient.core.navigation

import androidx.annotation.IdRes

interface Navigator {
    fun navigateTo(@IdRes navigationId: Int)
}
