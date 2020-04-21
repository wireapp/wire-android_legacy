package com.waz.zclient.feature.settings.devices.list.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.core.ui.list.RecyclerViewItemClickListener
import com.waz.zclient.core.utilities.DateAndTimeUtils
import com.waz.zclient.feature.settings.devices.ClientItem
import kotlinx.android.synthetic.main.item_view_devices.view.*

class DevicesRecyclerViewAdapter : RecyclerView.Adapter<DevicesViewHolder>() {

    private var deviceList: MutableList<ClientItem> = mutableListOf()

    private var itemClickListener: RecyclerViewItemClickListener<ClientItem>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
        return DevicesViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_view_devices, parent, false))
    }

    override fun getItemCount() = deviceList.size

    override fun onBindViewHolder(holder: DevicesViewHolder, position: Int) {
        val device = deviceList[position]
        holder.bind(device, itemClickListener)
    }

    fun updateList(updatedClientList: List<ClientItem>) {
        deviceList.clear()
        deviceList.addAll(updatedClientList)
        notifyDataSetChanged()
    }

    fun setItemClickedListener(@NonNull itemClickListener: RecyclerViewItemClickListener<ClientItem>) {
        this.itemClickListener = itemClickListener
    }
}

class DevicesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(clientItem: ClientItem, itemClickListener: RecyclerViewItemClickListener<ClientItem>?) {
        with(itemView) {
            item_device_title.text = clientItem.client.label
            val formattedDate = DateAndTimeUtils.getTimeStamp(clientItem.client.time)

            item_device_id.text = "ID: ${clientItem.client.id}\nActivated: $formattedDate"
            item_device_verification_icon.setImageResource(clientItem.verificationIcon())

            setOnClickListener {
                itemClickListener?.onItemClicked(clientItem)
            }
        }
    }
}
