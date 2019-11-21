package com.waz.zclient.core.toolbar

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity

interface WireToolbar {

    fun setContentView(activity: AppCompatActivity, @LayoutRes layoutRes: Int)

    fun setTitle(@StringRes title: Int)

    fun setTitle(title: CharSequence)

    fun setSubtitle(@StringRes subtitle: Int)

    fun setSubtitle(subtitle: CharSequence)

    fun hideToolbar()

    fun showBackArrow()
}
