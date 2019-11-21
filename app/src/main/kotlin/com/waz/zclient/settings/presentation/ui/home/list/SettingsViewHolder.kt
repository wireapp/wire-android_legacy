package com.waz.zclient.settings.presentation.ui.home.list

import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.settings.presentation.model.SettingsItem
import com.waz.zclient.utilities.config.ConfigHelper
import kotlinx.android.synthetic.main.item_settings.view.*

class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val configHelper: ConfigHelper = ConfigHelper()

    fun bind(settingsItem: SettingsItem) {
        itemView.settings_item_title.text = settingsItem.title
        itemView.settings_item_icon.text = settingsItem.icon

        when (configHelper.isDeveloperSettingsEnabled()) {
            true -> itemView.visibility = VISIBLE
            false -> itemView.visibility = GONE
        }
    }
}
