package com.waz.zclient.settings.support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.createScope
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE_ID
import com.waz.zclient.features.settings.support.SettingsSupportFragment
import kotlinx.android.synthetic.main.activity_settings_support.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

class SettingsSupportActivity : AppCompatActivity() {

    private val scope = createScope(
        scopeId = SETTINGS_SCOPE_ID,
        scopeName = SETTINGS_SCOPE
    )

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_support)
        setSupportActionBar(activitySettingsSupportToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        replaceFragment(R.id.activitySettingsSupportLayoutContainer, SettingsSupportFragment.newInstance(), false)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context) = Intent(context, SettingsSupportActivity::class.java)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.close()
    }
}
