package com.waz.zclient.settings.presentation.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.waz.zclient.R
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import kotlinx.android.synthetic.main.fragment_settings_devices.*

class DevicesFragment : Fragment() {

    private val viewModelFactory by lazy {
        SettingsViewModelFactory()
    }

    private lateinit var devicesViewModel: SettingsDevicesViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_settings_devices, container, false)
        initViewModel()
        return rootView
    }

    private fun initViewModel() {
        devicesViewModel = ViewModelProvider(this, viewModelFactory).get(SettingsDevicesViewModel::class.java)
        devicesViewModel.devicesData.observe(viewLifecycleOwner, Observer {
            when (it.status) {
                RequestResult.Status.SUCCESS -> {
                    tv_current_device.text = it.data?.toString()
                }
                RequestResult.Status.ERROR -> {
                    tv_current_device.text = it.message
                }
                else ->
                    tv_current_device.text = "Loading, please wait..."
            }
        })
    }

    companion object {
        fun newInstance() = DevicesFragment()
    }
}
