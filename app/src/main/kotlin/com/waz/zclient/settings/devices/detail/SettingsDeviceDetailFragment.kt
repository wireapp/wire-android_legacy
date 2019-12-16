package com.waz.zclient.settings.devices.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.settings.devices.SettingsDeviceViewModelFactory
import com.waz.zclient.settings.devices.model.ClientItem
import kotlinx.android.synthetic.main.fragment_device_detail.*

class SettingsDeviceDetailFragment : Fragment() {

    private lateinit var deviceDetailsViewModel: SettingsDeviceDetailViewModel

    private val viewModelFactory by lazy {
        SettingsDeviceViewModelFactory()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_device_detail, container, false)
        initViewModel()
        return rootView
    }

    private fun initViewModel() {
        deviceDetailsViewModel = ViewModelProvider(this, viewModelFactory).get(SettingsDeviceDetailViewModel::class.java).also { viewModel ->
            viewModel.currentDevice.observe(viewLifecycleOwner) { clientItem ->
                bindDataToView(clientItem)
            }

            viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
                bindLoading(isLoading)
            }

            viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
                bindError(errorMessage)
            }
        }
    }

    private fun bindError(errorMessage: String?) {
        //Show error when we need to
    }

    private fun bindLoading(loading: Boolean?) {
        //Show visibility of loading indicator
    }

    private fun bindDataToView(clientItem: ClientItem) {
        device_detail_id.text = clientItem.client.id
        device_detail_name.text = clientItem.client.label
        device_detail_activated.text = clientItem.client.time
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenResumed {
            val id = arguments?.getString(DEVICE_ID_BUNDLE_KEY)
            id?.let { deviceDetailsViewModel.loadData(it) }
        }
    }

    companion object {

        private const val DEVICE_ID_BUNDLE_KEY = "deviceIdBundleKey"

        fun newInstance(deviceId: String) = SettingsDeviceDetailFragment()
            .withArgs {
                putString(DEVICE_ID_BUNDLE_KEY, deviceId)
            }
    }
}
