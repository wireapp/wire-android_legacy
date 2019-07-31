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

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading.Implicits.Background
import com.waz.zclient.BuildConfig
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityCheckList.{Action, Check}

import scala.collection.mutable.ListBuffer

class SecurityCheckActivity extends AppCompatActivity with DerivedLogTag {

  private implicit val context: Context = this

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    
    securityChecklist.canProceed.foreach {
      case true =>
        setResult(Activity.RESULT_OK)
        finish()
      case false =>
        info(l"App is blocked.")
    }
  }

  private def securityChecklist: SecurityCheckList = {
    val checksAndActions = new ListBuffer[(Check, List[Action])]()

    if (BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT) {
      checksAndActions += RootDetectionCheck -> List(
        new WipeDataAction(),
        new BlockWithDialogAction("Root detected", "Wire is blocked"))
    }

    new SecurityCheckList(checksAndActions.toList)
  }
}

object SecurityCheckActivity {
  val RUN_SECURITY_CHECKS_REQUEST_CODE = 0
}
