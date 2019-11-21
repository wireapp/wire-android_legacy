package com.waz.zclient.settings.presentation.ui.misc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.toolbar.ToolbarCompat
import com.waz.zclient.core.toolbar.WireToolbar
import com.waz.zclient.utilities.extension.replaceFragment

class SettingsActivity : AppCompatActivity() {

    private var toolbar: WireToolbar = ToolbarCompat()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toolbar.setContentView(this, R.layout.activity_settings_new)
        toolbar.showBackArrow()

        replaceFragment(R.id.fragment_container, SettingsFragment.newInstance())
    }
}
