package com.waz.zclient.core.toolbar

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.waz.zclient.R
import com.waz.zclient.utilities.extension.getLabel

class WireToolbar {

    private lateinit var toolbar: Toolbar

    fun setContentView(activity: AppCompatActivity, layoutRes: Int) {

        val rootView = LayoutInflater.from(activity).inflate(R.layout.toolbar_container, null, false)

        toolbar = rootView.findViewById(R.id.toolbar)
        toolbar.title = activity.getLabel()
        activity.setSupportActionBar(toolbar)

        val layoutContainer = rootView.findViewById<FrameLayout>(R.id.layout_container)
        LayoutInflater.from(activity).inflate(layoutRes, layoutContainer, true)

        activity.setContentView(rootView)
    }

    fun setTitle(@StringRes title: Int) {
        toolbar.setTitle(title)
    }

    fun setTitle(title: CharSequence) {
        toolbar.title = title
    }

    fun setSubtitle(@StringRes subtitle: Int) {
        toolbar.setTitle(subtitle)
    }

    fun setSubtitle(subtitle: CharSequence) {
        toolbar.subtitle = subtitle
    }

    fun hideToolbar() {
        toolbar.visibility = View.GONE
    }

    fun showBackArrow() {
        toolbar.navigationIcon?.setVisible(true, false)
    }


}
