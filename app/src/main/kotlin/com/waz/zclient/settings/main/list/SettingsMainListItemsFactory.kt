package com.waz.zclient.settings.main.list

import android.content.Context
import com.waz.zclient.R
import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.stringArrayFromResource
import com.waz.zclient.settings.main.model.SettingsMainItem

class SettingsMainListItemsFactory private constructor() {

    companion object {
        fun generateList(context: Context): List<SettingsMainItem> {
            val titles =
                context.stringArrayFromResource(R.array.settings_titles)
                    .zip(context.stringArrayFromResource(R.array.settings_icons))
            val devTitles =
                if (Config.developerSettingsEnabled())
                    context.stringArrayFromResource(R.array.settings_developer_titles)
                        .zip(context.stringArrayFromResource(R.array.settings_developer_icons))
                else emptyList()
            return listOf(titles, devTitles).flatten().map { SettingsMainItem(it.first, it.second) }
        }
    }
}
