package com.waz.zclient.settings.presentation.ui.home.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.settings.presentation.ui.home.model.SettingsItem
import kotlinx.android.synthetic.main.item_settings.view.*

class SettingsListAdapter(private val settingsItems: List<SettingsItem>, private val listener: OnItemClickListener) : RecyclerView.Adapter<SettingsListAdapter.SettingsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        holder.bind(settingsItems[position])
    }

    override fun getItemCount(): Int = settingsItems.size

    inner class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(settingsItem: SettingsItem) {
            with(itemView) {
                settings_item_title.text = settingsItem.title
                settings_item_icon.text = settingsItem.icon
                setOnClickListener { listener.onItemClicked(position) }
            }
        }
    }
}
