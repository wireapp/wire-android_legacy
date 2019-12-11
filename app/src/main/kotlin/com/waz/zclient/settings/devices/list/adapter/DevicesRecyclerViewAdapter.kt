package com.waz.zclient.settings.devices.list.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.core.lists.RecyclerViewItemClickListener
import com.waz.zclient.settings.devices.model.ClientItem
import com.waz.zclient.utilities.DateAndTimeUtils

class DevicesRecyclerViewAdapter : RecyclerView.Adapter<DevicesViewHolder>() {

    private var deviceList: MutableList<ClientItem> = ArrayList()

    private var itemClickListener: RecyclerViewItemClickListener<ClientItem>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
        return DevicesViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_view_devices, parent, false))
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

    fun setOnItemClickedListener(@NonNull itemClickListener: RecyclerViewItemClickListener<ClientItem>) {
        this.itemClickListener = itemClickListener
    }
}

class DevicesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val deviceName: TextView by lazy {
        itemView.findViewById<TextView>(R.id.item_device_title)
    }

    private val deviceId: TextView by lazy {
        itemView.findViewById<TextView>(R.id.item_device_id)
    }

    private val deviceVerifiedIcon: ImageView by lazy {
        itemView.findViewById<ImageView>(R.id.item_device_verification_icon)
    }

    fun bind(clientItem: ClientItem, itemClickListener: RecyclerViewItemClickListener<ClientItem>?) {
        deviceName.text = clientItem.client.label
        val formattedDate = DateAndTimeUtils.getTimeStamp(clientItem.client.time)
        deviceId.text = "ID: ${clientItem.client.id}\nActivated: ${formattedDate}"
        deviceVerifiedIcon.setImageResource(clientItem.verificationIcon())
        itemView.setOnClickListener { itemClickListener?.onItemClicked(clientItem) }
    }
}
