package com.waz.zclient.settings.main.list

import android.content.Context
import com.waz.zclient.R
import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.stringArrayFromResource
import com.waz.zclient.settings.main.model.SettingsMainItem

class SettingsMainListFactory {

    companion object {
        fun generateList(context: Context): List<SettingsMainItem> {
            val titles = context.stringArrayFromResource(R.array.settings_titles)
            val icons = context.stringArrayFromResource(R.array.settings_icons)
            val developerTitles = context.stringArrayFromResource(R.array.settings_developer_titles)
            val developerIcons = context.stringArrayFromResource(R.array.settings_developer_icons)

            val settingItems = ArrayList<SettingsMainItem>()
            for (i in titles.indices) {
                settingItems.add(SettingsMainItem(titles[i], icons[i]))
            }
            if (Config.developerSettingsEnabled()) {
                for (i in developerTitles.indices) {
                    settingItems.add(SettingsMainItem(developerTitles[i], developerIcons[i]))
                }
            }
            return settingItems
        }
    }
}
