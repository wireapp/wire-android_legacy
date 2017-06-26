/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.preferences

import android.content.{ClipData, ClipboardManager, Context}
import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT
import android.widget.Toast
import com.waz.content.GlobalPreferences
import com.waz.content.Preferences.PrefKey
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.preferences.dialogs.AccountSignInPreference
import com.waz.zclient.utils.{DebugUtils, RichPreference}

class DeveloperPreferences extends BasePreferenceFragment {

  lazy val globalPrefs = inject[GlobalPreferences]

  private lazy val lastCallSessionIdPreference = returning(findPref[Preference](R.string.pref_dev_avs_last_call_session_id_key)) { p =>
    p.onClick(copyLastCallSessionIdToClipboard())
    p.setSummary(getPreferenceManager
      .getSharedPreferences
      .getString(getString(R.string.pref_dev_avs_last_call_session_id_key), getString(R.string.pref_dev_avs_last_call_session_id_not_available)))

    //TODO make prefkey/preference method that takes in a context/resource id
    globalPrefs.preference(PrefKey[String](getString(R.string.pref_dev_avs_last_call_session_id_key))).signal.on(Threading.Ui)(p.setSummary)
  }

  private lazy val versionInfoPreference = returning(findPref[Preference](R.string.pref_dev_version_info_id_key))(_.setSummary(DebugUtils.getVersion(getContext)))

  private lazy val accountSignInPreference = returning(findPref[Preference](R.string.pref_dev_category_sign_in_account_key)) {
    _.onClick(getChildFragmentManager.beginTransaction
      .add(new AccountSignInPreference, AccountSignInPreference.FragmentTag)
      .addToBackStack(AccountSignInPreference.FragmentTag)
      .commit)
  }

  override def onCreatePreferences2(savedInstanceState: Bundle, rootKey: String) = {
    super.onCreatePreferences2(savedInstanceState, rootKey)
    addPreferencesFromResource(R.xml.preferences_developer)

    //trigger lazy initialisation
    lastCallSessionIdPreference
    versionInfoPreference
    accountSignInPreference
  }

  private def copyLastCallSessionIdToClipboard() = {
    val lastCallSessionIdKey = getString(R.string.pref_dev_avs_last_call_session_id_key)
    val lastCallSessionId = getPreferenceManager.getSharedPreferences.getString(lastCallSessionIdKey, getString(R.string.pref_dev_avs_last_call_session_id_not_available))
    val clipboard = getActivity.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clip = ClipData.newPlainText(getString(R.string.pref_dev_avs_last_call_session_id_title), lastCallSessionId)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(getActivity, getString(R.string.pref_dev_avs_last_call_session_id_copied_to_clipboard), Toast.LENGTH_SHORT).show()
  }

  override def onDestroyView() = {
    lastCallSessionIdPreference.setOnPreferenceClickListener(null)
    super.onDestroyView()
  }
}

object DeveloperPreferences {
  def newInstance(rootKey: String, extras: Bundle) = {
    returning (new DeveloperPreferences) { f =>
      f.setArguments(returning(if (extras == null) new Bundle else new Bundle(extras)) { b =>
        b.putString(ARG_PREFERENCE_ROOT, rootKey)
      })
    }
  }
}
