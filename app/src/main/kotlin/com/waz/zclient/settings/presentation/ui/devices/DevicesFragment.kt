package com.waz.zclient.settings.presentation.ui.devices

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
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import com.waz.zclient.settings.presentation.ui.devices.list.DevicesRecyclerViewAdapter

class DevicesFragment : Fragment() {

    private lateinit var devicesViewModel: SettingsDevicesViewModel

    private lateinit var devicesRecyclerView: RecyclerView

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
    }

    private fun initViewModel() {
        devicesViewModel = ViewModelProvider(this, viewModelFactory).get(SettingsDevicesViewModel::class.java).also { viewModel ->
            viewModel.state.observe(viewLifecycleOwner, Observer {
                when (val clientState = it!!) {
                    is SettingsDevicesViewModel.ClientsState.Success -> devicesAdapter.updateList(clientState.clients)
                }
            })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        devicesViewModel.loadData()
    }

    companion object {
        fun newInstance() = DevicesFragment()
    }
}
