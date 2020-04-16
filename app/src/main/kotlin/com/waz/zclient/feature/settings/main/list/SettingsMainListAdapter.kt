package com.waz.zclient.feature.settings.main.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.core.ui.list.OnItemClickListener
import com.waz.zclient.feature.settings.main.SettingsMainItem
import kotlinx.android.synthetic.main.item_settings.view.*

class SettingsMainListAdapter(
    private val settingsItems: List<SettingsMainItem>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<SettingsMainListAdapter.SettingsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        holder.bind(settingsItems[position])
    }

    override fun getItemCount(): Int = settingsItems.size

    inner class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(settingsItem: SettingsMainItem) {
            with(itemView) {
                settings_item_title.text = settingsItem.title
                settings_item_icon.text = settingsItem.icon
                setOnClickListener { listener.onItemClicked(adapterPosition) }
            }
        }
    }
}
