package com.waz.zclient.settings.presentation.ui.home.list

import com.waz.zclient.R
import com.waz.zclient.settings.presentation.model.SettingsItem
import com.waz.zclient.utilities.resources.ResourceManager

class SettingsListFactory {

    companion object {
        fun generateList(resourceManager: ResourceManager): List<SettingsItem> {
            val titles = resourceManager.getStringArray(R.array.settings_titles)
            val icons = resourceManager.getStringArray(R.array.settings_icons)
            val settingItems = ArrayList<SettingsItem>()
            for (i in titles.indices) {
                settingItems.add(SettingsItem(titles[i], icons[i]))
            }
            return settingItems
        }
    }
}
