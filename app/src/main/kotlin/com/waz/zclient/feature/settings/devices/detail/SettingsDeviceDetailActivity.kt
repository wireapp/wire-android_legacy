package com.waz.zclient.feature.settings.devices.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import kotlinx.android.synthetic.main.activity_device_detail.*

class SettingsDeviceDetailActivity : AppCompatActivity(R.layout.activity_device_detail) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbar()
        startDeviceDetailsFragment()
    }

    private fun startDeviceDetailsFragment() {
        val deviceId = intent.getStringExtra(DEVICE_ID_BUNDLE_KEY)
        replaceFragment(R.id.layout_container, SettingsDeviceDetailFragment.newInstance(deviceId), false)
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {

        private const val DEVICE_ID_BUNDLE_KEY = "deviceIdBundleKey"

        fun newIntent(context: Context, deviceId: String): Intent {
            return Intent(context, SettingsDeviceDetailActivity::class.java)
                .putExtra(DEVICE_ID_BUNDLE_KEY, deviceId)
        }
    }
}
