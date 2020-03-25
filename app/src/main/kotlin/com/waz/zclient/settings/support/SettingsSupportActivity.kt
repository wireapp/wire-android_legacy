package com.waz.zclient.settings.support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.createScope
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.core.ui.backgroundasset.ActivityBackgroundAssetObserver
import com.waz.zclient.core.ui.backgroundasset.BackgroundAssetObserver
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE_ID
import com.waz.zclient.features.settings.support.SettingsSupportFragment
import kotlinx.android.synthetic.main.activity_settings_support.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsSupportActivity : AppCompatActivity(R.layout.activity_settings_support),
    BackgroundAssetObserver<AppCompatActivity> by ActivityBackgroundAssetObserver() {

    private val scope = createScope(
        scopeId = SETTINGS_SCOPE_ID,
        scopeName = SETTINGS_SCOPE
    )

    private val viewModel by viewModel<SettingsSupportMainViewModel>(SETTINGS_SCOPE_ID)

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(activitySettingsSupportToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        replaceFragment(R.id.activitySettingsSupportLayoutContainer, SettingsSupportFragment.newInstance(), false)
        loadBackground(this, viewModel, activitySettingsSupportConstraintLayout)
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
