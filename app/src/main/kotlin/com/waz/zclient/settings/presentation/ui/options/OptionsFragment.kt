package com.waz.zclient.settings.presentation.ui.options

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.BuildConfig
import com.waz.zclient.R
import com.waz.zclient.utilities.config.ConfigHelper
import com.waz.zclient.utilities.extension.remove

class OptionsFragment : PreferenceFragmentCompat() {


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_options, rootKey)

        val prefShareContacts: Preference? = findPreference(resources.getString(R.string.pref_key_share_contacts))
        prefShareContacts?.remove()

        val prefVibrate: Preference? = findPreference(resources.getString(R.string.pref_key_vibrate))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            prefVibrate?.title = getString(R.string.pref_options_vibration_title_o)
        }

        val prefAppLock: Preference? = findPreference(resources.getString(R.string.pref_key_app_lock))
        if (ConfigHelper.isAppLockForced()) {
            prefAppLock?.remove()
        } else {
            prefAppLock?.summary = getString(R.string.pref_options_app_lock_summary, BuildConfig.APP_LOCK_TIMEOUT.toString())
        }

        val prefHideScreenContent: Preference? = findPreference(resources.getString(R.string.pref_key_hide_screen_content))
        if (ConfigHelper.isHideScreenContentForced()) {
            prefHideScreenContent?.remove()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_options_screen_title)
    }

    companion object {
        fun newInstance() = OptionsFragment()
    }
}


