package com.waz.zclient.core.toolbar

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import com.waz.zclient.R

class ToolbarCompat : WireToolbar {

    private lateinit var toolbar: Toolbar

    override fun setContentView(activity: AppCompatActivity, layoutRes: Int) {

        val rootView = LayoutInflater.from(activity).inflate(R.layout.toolbar_container, null, false)

        toolbar = rootView.findViewById(R.id.toolbar)
        activity.setSupportActionBar(toolbar)

        val layoutContainer = rootView.findViewById<FrameLayout>(R.id.layout_container)
        LayoutInflater.from(activity).inflate(layoutRes, layoutContainer, true)

        activity.setContentView(rootView)
    }

    override fun setTitle(@StringRes title: Int) {
        toolbar.setTitle(title)
    }

    override fun setTitle(title: CharSequence) {
        toolbar.title = title
    }

    override fun setSubtitle(@StringRes subtitle: Int) {
        toolbar.setTitle(subtitle)
    }

    override fun setSubtitle(subtitle: CharSequence) {
        toolbar.subtitle = subtitle
    }

    override fun hideToolbar() {
        toolbar.visibility = View.GONE
    }

    override fun showBackArrow() {
        toolbar.navigationIcon?.setVisible(true, false)
    }
}
