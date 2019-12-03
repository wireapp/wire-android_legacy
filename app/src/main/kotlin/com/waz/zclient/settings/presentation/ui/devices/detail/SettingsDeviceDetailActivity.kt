package com.waz.zclient.settings.presentation.ui.devices.detail

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.settings.presentation.ui.devices.SettingsDeviceConstants
import com.waz.zclient.utilities.extension.replaceFragment
import kotlinx.android.synthetic.main.activity_device_detail.*

class SettingsDeviceDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_detail)
        initToolbar()
        startDeviceDetailsFragment()
    }

    private fun startDeviceDetailsFragment() {
        val deviceId = intent.getStringExtra(SettingsDeviceConstants.DEVICE_ID_BUNDLE_KEY)
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
}
