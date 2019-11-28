package com.waz.zclient.settings.presentation.ui.devices.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.core.lists.RecyclerViewItemClickListener
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import com.waz.zclient.settings.presentation.ui.devices.list.adapter.DevicesRecyclerViewAdapter
import com.waz.zclient.settings.presentation.ui.devices.list.adapter.DevicesViewHolder
import com.waz.zclient.settings.presentation.ui.devices.model.ClientItem

class SettingsDeviceListFragment : Fragment() {

    private lateinit var deviceListViewModel: SettingsDeviceListViewModel

    private lateinit var devicesRecyclerView: RecyclerView

    private lateinit var singleDeviceViewHolder: DevicesViewHolder

    private val itemClickListener by lazy {
        object : RecyclerViewItemClickListener<ClientItem> {
            override fun onItemClicked(item: ClientItem) {

            }
        }
    }

    private val viewModelFactory by lazy {
        SettingsViewModelFactory()
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

    private fun initRecyclerView(rootView: View) {
        devicesRecyclerView = rootView.findViewById(R.id.device_list_recycler_view)
        val linearLayoutInflater = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        devicesRecyclerView.layoutManager = linearLayoutInflater
        devicesRecyclerView.adapter = devicesAdapter
        devicesAdapter.setOnItemClickedListener(itemClickListener)

        singleDeviceViewHolder = DevicesViewHolder(rootView)
    }

    private fun initViewModel() {
        deviceListViewModel = ViewModelProvider(this, viewModelFactory).get(SettingsDeviceListViewModel::class.java).also { viewModel ->
            viewModel.state.observe(viewLifecycleOwner, Observer { presentationState ->
                when (presentationState) {
                    //Potential states (not final)
                    is ClientPresentationState.Empty -> print("Show warning for empty list here")
                    is ClientPresentationState.Loading -> print("Show spinner of some form to notify the user we're making a request?")
                    is ClientPresentationState.Error -> print("Generic error scenario for all requests here")
                    else -> print("Oops this state doesn't exist.")
                }

            })

            viewModel.currentDevice.observe(viewLifecycleOwner, Observer { currentDevice ->
                bindCurrentDevice(currentDevice)
            })

            viewModel.otherDevices.observe(viewLifecycleOwner, Observer { otherDevices ->
                devicesAdapter.updateList(otherDevices)
            })
        }
    }

    private fun bindCurrentDevice(currentDevice: ClientItem) {
        singleDeviceViewHolder.bind(currentDevice, itemClickListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceListViewModel.loadData()
    }

    companion object {
        fun newInstance() = SettingsDeviceListFragment()
    }
}
