package com.waz.zclient.settings.presentation.ui.misc

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_settings.view.*

class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(settingsTitle: String, settingsIcon: String) {
        itemView.settings_item_title.text = settingsTitle
        itemView.settings_item_icon.text = settingsIcon
    }
}
