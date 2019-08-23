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
package com.waz.zclient.security.actions

import android.app.AlertDialog
import android.content.Context
import com.waz.threading.Threading.Implicits.Ui
import com.waz.zclient.security.SecurityChecklist

import scala.concurrent.Future

class BlockWithDialogAction(title: String, message: String)(implicit context: Context) extends SecurityChecklist.Action {

  override def execute(): Future[Unit] = {
    Future {
      new AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(false)
        .create()
        .show()
    }
  }
}

object BlockWithDialogAction {
  import com.waz.zclient.utils.ContextUtils.getString

  def apply(titleResId: Int, messageResId: Int)(implicit context: Context): BlockWithDialogAction = {
    val title = getString(titleResId)
    val message = getString(messageResId)
    new BlockWithDialogAction(title, message)
  }
}
