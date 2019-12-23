package com.waz.zclient.settings.devices.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.core.lists.RecyclerViewItemClickListener
import com.waz.zclient.settings.devices.detail.SettingsDeviceDetailActivity
import com.waz.zclient.settings.devices.list.adapter.DevicesRecyclerViewAdapter
import com.waz.zclient.settings.devices.list.adapter.DevicesViewHolder
import com.waz.zclient.settings.devices.model.ClientItem
import org.koin.android.viewmodel.ext.android.viewModel

class SettingsDeviceListFragment : Fragment() {

    private val deviceListViewModel: SettingsDeviceListViewModel by viewModel()

    private lateinit var devicesRecyclerView: RecyclerView

    private lateinit var singleDeviceViewHolder: DevicesViewHolder

    private val itemClickListener by lazy {
        object : RecyclerViewItemClickListener<ClientItem> {
            override fun onItemClicked(item: ClientItem) {
                navigateToDeviceDetails(item.client.id)
            }
        }
    }

    private val devicesAdapter by lazy {
        DevicesRecyclerViewAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_settings_devices, container, false)
        initRecyclerView(rootView)
        initViewModel()
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenResumed {
            deviceListViewModel.loadData()
        }
    }

    private fun initRecyclerView(rootView: View) {
        devicesRecyclerView = rootView.findViewById(R.id.device_list_recycler_view)
        val linearLayoutManager = LinearLayoutManager(requireContext())
        devicesRecyclerView.layoutManager = linearLayoutManager
        devicesRecyclerView.adapter = devicesAdapter
        devicesAdapter.setItemClickedListener(itemClickListener)

        singleDeviceViewHolder = DevicesViewHolder(rootView)
    }

    private fun initViewModel() {
        with(deviceListViewModel) {
            loading.observe(viewLifecycleOwner) { isLoading ->
                updateLoadingVisibility(isLoading)
            }
            error.observe(viewLifecycleOwner) { errorMessage ->
                showErrorMessage(errorMessage)
            }
            currentDevice.observe(viewLifecycleOwner) { currentDevice ->
                bindCurrentDevice(currentDevice)
            }
            otherDevices.observe(viewLifecycleOwner) { otherDevices ->
                devicesAdapter.updateList(otherDevices)
            }
        }
    }

    private fun navigateToDeviceDetails(deviceId: String) {
        val intent = SettingsDeviceDetailActivity.newIntent(requireActivity(), deviceId)
        startActivity(intent)
    }

    private fun showErrorMessage(errorMessage: String) {
        Toast.makeText(requireActivity(), errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun updateLoadingVisibility(isLoading: Boolean?) {
        //Show hide progress indicator
    }

    private fun bindCurrentDevice(currentDevice: ClientItem) {
        singleDeviceViewHolder.bind(currentDevice, itemClickListener)
    }

    companion object {
        fun newInstance() = SettingsDeviceListFragment()
    }
}
