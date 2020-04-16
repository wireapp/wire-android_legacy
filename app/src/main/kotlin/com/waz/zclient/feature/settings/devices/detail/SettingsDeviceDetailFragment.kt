package com.waz.zclient.feature.settings.devices.detail

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.feature.settings.devices.ClientItem
import com.waz.zclient.feature.settings.di.SETTINGS_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_device_detail.*

class SettingsDeviceDetailFragment : Fragment(R.layout.fragment_device_detail) {

    private val deviceDetailsViewModel by viewModel<SettingsDeviceDetailViewModel>(SETTINGS_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeCurrentDeviceData()
        observeLoadingData()
        observeErrorData()
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launchWhenResumed {
            val id = arguments?.getString(DEVICE_ID_BUNDLE_KEY)
            id?.let { deviceDetailsViewModel.loadData(it) }
        }
    }

    private fun observeCurrentDeviceData() =
        deviceDetailsViewModel.currentDevice.observe(viewLifecycleOwner) { clientItem ->
            bindDataToView(clientItem)
        }

    private fun observeLoadingData() =
        deviceDetailsViewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            bindLoading(isLoading)
        }

    private fun observeErrorData() =
        deviceDetailsViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            bindError(errorMessage)
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

    companion object {

        private const val DEVICE_ID_BUNDLE_KEY = "deviceIdBundleKey"

        fun newInstance(deviceId: String) = SettingsDeviceDetailFragment()
            .withArgs {
                putString(DEVICE_ID_BUNDLE_KEY, deviceId)
            }
    }
}
