package com.waz.zclient.settings.presentation.ui.devices.list.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.core.lists.RecyclerViewItemClickListener
import com.waz.zclient.settings.presentation.ui.devices.model.ClientItem
import com.waz.zclient.utilities.utils.DateAndTimeUtils

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

    fun bind(client: ClientItem, itemClickListener: RecyclerViewItemClickListener<ClientItem>?) {
        deviceName.text = client.label
        val formattedDate = DateAndTimeUtils.getTimeStamp(client.time)
        deviceId.text = "ID: ${client.id}\nActivated: ${formattedDate}"
        deviceVerifiedIcon.setImageResource(client.verificationIcon)
        itemView.setOnClickListener { itemClickListener?.onItemClicked(client) }
    }
}
