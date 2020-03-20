package com.waz.zclient.features.settings.main

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.observe
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.waz.zclient.R
import com.waz.zclient.core.extension.createScope
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE
import com.waz.zclient.features.settings.di.SETTINGS_SCOPE_ID
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

data class Pic(val id: String)

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
            val opt = RequestOptions().also {
                //TODO: convert to Kotlin
//                it.transform(CenterCrop(),
//                    ScaleTransformation(1.4f),
//                    BlurTransformation(),
//                    DarkenTransformation(148, 2f))
            }

            Glide.with(this).load(it).apply(opt)
                .into(object : CustomViewTarget<ViewGroup, Drawable>(activitySettingsMainConstraintLayout) {
                    override fun onLoadFailed(errorDrawable: Drawable?) {}

                    override fun onResourceCleared(placeholder: Drawable?) {}

                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        activitySettingsMainConstraintLayout.background = resource
                    }
                })
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
