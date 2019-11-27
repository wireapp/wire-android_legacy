package com.waz.zclient.settings.presentation.ui.devices.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.settings.presentation.ui.devices.model.ClientsUiModel
import timber.log.Timber

class DevicesRecyclerViewAdapter : RecyclerView.Adapter<DevicesRecyclerViewAdapter.DevicesViewHolder>() {

    private var deviceList = ArrayList<ClientsUiModel>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
        return DevicesViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_view_devices, parent, false))
    }

    override fun getItemCount() = deviceList.size

    override fun onBindViewHolder(holder: DevicesViewHolder, position: Int) {
        val device = deviceList[position]
        holder.bind(device)
    }

    fun updateList(updatedClientList: List<ClientsUiModel> = ArrayList()) {
        Timber.e(javaClass.simpleName, updatedClientList.size)
        deviceList.clear()
        deviceList.addAll(updatedClientList)
        notifyDataSetChanged()
    }

    inner class DevicesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val deviceName: TextView by lazy {
            itemView.findViewById<TextView>(R.id.item_device_title)
        }

        private val deviceId: TextView by lazy {
            itemView.findViewById<TextView>(R.id.item_device_id)
        }

        private val deviceActivation: TextView by lazy {
            itemView.findViewById<TextView>(R.id.item_device_activated_time)
        }

        fun bind(clientEntity: ClientsUiModel) {
            deviceName.text = clientEntity.label
            deviceId.text = "iD: ${clientEntity.id}"
            deviceActivation.text = "Activated: ${clientEntity.time}"

        }
    }
}
