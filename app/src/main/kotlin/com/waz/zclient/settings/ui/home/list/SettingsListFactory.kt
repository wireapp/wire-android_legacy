package com.waz.zclient.settings.ui.home.list

import android.content.Context
import com.waz.zclient.R
import com.waz.zclient.settings.model.SettingsItem
import com.waz.zclient.utilities.config.Config
import com.waz.zclient.utilities.extension.stringArrayFromResource

class SettingsListFactory {

    companion object {
        fun generateList(context: Context): List<SettingsItem> {
            val titles = context.stringArrayFromResource(R.array.settings_titles)
            val icons = context.stringArrayFromResource(R.array.settings_icons)
            val developerTitles = context.stringArrayFromResource(R.array.settings_developer_titles)
            val developerIcons = context.stringArrayFromResource(R.array.settings_developer_icons)

            val settingItems = ArrayList<SettingsItem>()
            for (i in titles.indices) {
                settingItems.add(SettingsItem(titles[i], icons[i]))
            }
            if (Config.isDeveloperSettingsEnabled()) {
                for (i in developerTitles.indices) {
                    settingItems.add(SettingsItem(developerTitles[i], developerIcons[i]))
                }
            }
            return settingItems
        }
    }
}
