/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.preferences.dialogs

import android.os.Bundle
import com.waz.content.UserPreferences
import com.waz.media.manager.context.IntensityLevel
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.preferences.dialogs.SoundLevelDialog._

class SoundLevelDialog extends PreferenceListDialog {

  protected lazy val zms = inject[Signal[ZMessaging]]

  override protected lazy val title: String = getString(R.string.pref_options_sounds_title)
  override protected lazy val names: Array[String] = Array(
    getString(R.string.pref_options_sounds_all),
    getString(R.string.pref_options_sounds_new_conversations_and_talks_only),
    getString(R.string.pref_options_sounds_none))
  override protected lazy val defaultValue: Int = Option(getArguments.getInt(DefaultValueArg)).getOrElse(0)

  override protected def updatePref(which: Int): Unit = {
    val value =
      which match {
        case 0 => IntensityLevel.FULL
        case 1 => IntensityLevel.SOME
        case _ => IntensityLevel.NONE
      }
    zms.map(_.userPrefs.preference(UserPreferences.Sounds)).head.map(_.update(value))(Threading.Ui)
  }
}

object SoundLevelDialog {
  val Tag = SoundLevelDialog.getClass.getSimpleName

  protected val DefaultValueArg = "DefaultValueArg"

  def apply(intensityLevel: IntensityLevel): SoundLevelDialog = {
    returning(new SoundLevelDialog()){ fragment =>
      val bundle = new Bundle()
      val default = intensityLevel match {
        case IntensityLevel.FULL => 0
        case IntensityLevel.SOME => 1
        case IntensityLevel.NONE => 2
      }
      bundle.putInt(DefaultValueArg, default)
      fragment.setArguments(bundle)
    }
  }

}
