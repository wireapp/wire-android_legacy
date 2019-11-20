package com.waz.zclient.settings.presentation.ui.misc

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R

class SettingsListAdapter(private val settingsTitles: Array<String>, private val settingsIcons: Array<String>)
    : RecyclerView.Adapter<SettingsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        holder.bind(settingsTitles[position], settingsIcons[position])
    }

    override fun getItemCount(): Int = settingsTitles.size

}
