package com.waz.zclient.settings.devices.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.settings.devices.model.ClientItem
import kotlinx.android.synthetic.main.fragment_device_detail.*
import org.koin.android.viewmodel.ext.android.viewModel

class SettingsDeviceDetailFragment : Fragment() {

    private val deviceDetailsViewModel: SettingsDeviceDetailViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_device_detail, container, false)
        initViewModel()
        return rootView
    }

    private fun initViewModel() {
        with(deviceDetailsViewModel) {
            currentDevice.observe(viewLifecycleOwner) { clientItem ->
                bindDataToView(clientItem)
            }

            loading.observe(viewLifecycleOwner) { isLoading ->
                bindLoading(isLoading)
            }

            error.observe(viewLifecycleOwner) { errorMessage ->
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
