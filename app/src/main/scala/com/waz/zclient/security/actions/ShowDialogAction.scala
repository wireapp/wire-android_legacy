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
import android.content.{Context, DialogInterface}
import com.waz.threading.Threading.Implicits.Ui
import com.waz.zclient.security.SecurityChecklist
import com.waz.zclient.utils.ContextUtils.getString

import scala.concurrent.Future

class ShowDialogAction(title: String,
                       message: String,
                       actionTitle: String,
                       action: () => Unit)
                      (implicit context: Context) extends SecurityChecklist.Action {

  override def execute(): Future[Unit] = {
    Future {
      val listener = new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = action()
      }

      new AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(false)
        .setPositiveButton(actionTitle, listener)
        .create()
        .show()
    }
  }
}

object ShowDialogAction {

  def apply(titleResId: Int,
            messageResId: Int,
            actionTitleResId: Int,
            action: () => Unit)
           (implicit context: Context): ShowDialogAction = {

    val title = getString(titleResId)
    val message = getString(messageResId)
    val actionTitle = getString(actionTitleResId)
    new ShowDialogAction(title, message, actionTitle, action)
  }
}

