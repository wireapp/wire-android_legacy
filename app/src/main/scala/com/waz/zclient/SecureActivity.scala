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
package com.waz.zclient

import android.app.Activity
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.log.LogUI._

class SecureActivity extends AppCompatActivity with DerivedLogTag {
  import SecurityCheckActivity._

  private var shouldRunSecurityChecks = true

  override def onStart(): Unit = {
    super.onStart()
    verbose(l"SECURITY: onCreate")

    if (shouldRunSecurityChecks) {
      startSecurityCheckActivity()
    }
  }

  private def startSecurityCheckActivity(): Unit = {
    verbose(l"SECURITY: starting security check activity")
    val intent = new Intent(this, classOf[SecurityCheckActivity])
    startActivityForResult(intent, RUN_SECURITY_CHECKS_REQUEST_CODE)
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)

    // TODO: Handle non ok results?
    (requestCode, resultCode) match {
      case (RUN_SECURITY_CHECKS_REQUEST_CODE, Activity.RESULT_OK) =>
        shouldRunSecurityChecks = false
        verbose(l"SECURITY: security checks complete")

      case _ =>
    }
  }
}
