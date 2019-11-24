package com.waz.zclient.settings.presentation.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.toolbar.WireToolbar
import com.waz.zclient.core.toolbar.WireToolbarImpl
import com.waz.zclient.utilities.extension.replaceFragment

class SettingsActivity : AppCompatActivity() {

    private var toolbar: WireToolbar = WireToolbarImpl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toolbar.setContentView(this, R.layout.activity_settings_new)
        toolbar.setTitle(getString(R.string.settings_title))
        toolbar.showBackArrow()
        replaceFragment(R.id.fragment_container, SettingsFragment.newInstance())
    }
}
