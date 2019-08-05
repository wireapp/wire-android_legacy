/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.security

import android.content.Context
import android.support.v7.app.AppCompatActivity
import com.waz.content.GlobalPreferences
import com.waz.zclient.security.SecurityChecklist.{Action, Check}
import com.waz.zclient.{ActivityHelper, BuildConfig, R}

import scala.collection.mutable.ListBuffer

class SecureActivity extends AppCompatActivity with ActivityHelper {

  private implicit val context: Context = this

  override def onStart(): Unit = {
    super.onStart()
    securityChecklist.run()
  }

  private def securityChecklist: SecurityChecklist = {
    val checksAndActions = new ListBuffer[(Check, List[Action])]()

    if (BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT) {
      val preferences = inject[GlobalPreferences]
      checksAndActions += RootDetectionCheck(preferences) -> List(
        new WipeDataAction(),
        BlockWithDialogAction(R.string.root_detected_dialog_title, R.string.root_detected_dialog_message)
      )
    }

    new SecurityChecklist(checksAndActions.toList)
  }
}
