package com.waz.zclient.core.toolbar

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.waz.zclient.R

class WireToolbar {

    private lateinit var toolbar: Toolbar

    fun contentView(activity: AppCompatActivity, layoutRes: Int) {

        val rootView = LayoutInflater.from(activity).inflate(R.layout.toolbar_container, null, false)

        toolbar = rootView.findViewById(R.id.toolbar)
        activity.setSupportActionBar(toolbar)

        val layoutContainer = rootView.findViewById<FrameLayout>(R.id.layout_container)
        LayoutInflater.from(activity).inflate(layoutRes, layoutContainer, true)

        activity.setContentView(rootView)
    }

    fun title(@StringRes title: Int) {
        toolbar.setTitle(title)
    }

    fun title(title: CharSequence) {
        toolbar.title = title
    }

    fun subtitle(@StringRes subtitle: Int) {
        toolbar.setTitle(subtitle)
    }

    fun subtitle(subtitle: CharSequence) {
        toolbar.subtitle = subtitle
    }

    fun hideToolbar() {
        toolbar.visibility = View.GONE
    }

    fun showBackArrow() {
        toolbar.navigationIcon?.setVisible(true, false)
    }


}
