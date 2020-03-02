package com.waz.zclient.features.settings.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.createScope
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE_ID
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

class SettingsMainActivity : AppCompatActivity(R.layout.activity_settings) {

    private val scope = createScope(
        scopeId = SETTINGS_SCOPE_ID,
        scopeName = SETTINGS_SCOPE
    )

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    //TODO Method level annotations as this is the top of the chain
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context) = Intent(context, SettingsMainActivity::class.java)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.close()
    }
}
