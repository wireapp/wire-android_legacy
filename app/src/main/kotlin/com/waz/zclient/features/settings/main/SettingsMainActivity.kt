package com.waz.zclient.features.settings.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.addTransformations
import com.waz.zclient.core.extension.createScope
import com.waz.zclient.core.extension.imageLoader
import com.waz.zclient.core.extension.intoBackground
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.core.images.transformations.AppBackgroundTransformations
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE_ID
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsMainActivity : AppCompatActivity(R.layout.activity_settings) {

    private val scope = createScope(
        scopeId = SETTINGS_SCOPE_ID,
        scopeName = SETTINGS_SCOPE
    )

    private val viewModel by viewModel<SettingsMainViewModel>(SETTINGS_SCOPE_ID)

    @InternalCoroutinesApi
    //TODO Method level annotations as this is the top of the chain
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        replaceFragment(R.id.activitySettingsMainLayoutContainer, SettingsMainFragment.newInstance())
        setBackgroundImage()
    }

    private fun setBackgroundImage() = viewModel.let {
        it.fetchBackgroundImage()
        it.backgroundAsset.observe(this) {
            imageLoader()
                .load(it)
                .addTransformations(*AppBackgroundTransformations.transformations(this))
                .intoBackground(activitySettingsMainConstraintLayout)
        }
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
