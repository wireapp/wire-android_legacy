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
package com.waz.zclient.appentry

import android.content.DialogInterface.OnDismissListener
import android.content.Intent.{ACTION_VIEW, FLAG_ACTIVITY_NEW_TASK}
import android.content.{Context, DialogInterface, Intent}
import android.net.Uri
import android.support.v7.app.AlertDialog
import com.waz.ZLog.error
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.ZLog.ImplicitTag.implicitLogTag
import scala.concurrent.{Future, Promise}

object AppEntryDialogs {
  def showTermsAndConditions(context: Context): Future[Boolean] = {
    val dialogResult = Promise[Boolean]()
    val dialog = new AlertDialog.Builder(context)
      .setPositiveButton(R.string.app_entry_dialog_accept, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = dialogResult.trySuccess(true)
      })
      .setNegativeButton(R.string.app_entry_dialog_cancel, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = dialogResult.trySuccess(false)
      })
      .setNeutralButton(R.string.app_entry_dialog_view, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = {
          onOpenUrl(context, context.getString(R.string.url_terms_of_service_teams))
          dialogResult.trySuccess(false)
        }
      })
      .setTitle(R.string.app_entry_tc_dialog_title)
      .setMessage(R.string.app_entry_tc_dialog_message)
      .setOnDismissListener(new OnDismissListener {
        override def onDismiss(dialog: DialogInterface): Unit = dialogResult.trySuccess(false)
      })
    dialog.show()
    dialogResult.future
  }

  def showNotificationsWarning(context: Context): Future[Boolean] = {
    val dialogResult = Promise[Boolean]()
    val dialog = new AlertDialog.Builder(context)
      .setPositiveButton(R.string.app_entry_dialog_accept, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = dialogResult.trySuccess(true)
      })
      .setNegativeButton(R.string.app_entry_dialog_cancel, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = dialogResult.trySuccess(false)
      })
      .setNeutralButton(R.string.app_entry_dialog_view, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = {
          onOpenUrl(context, context.getString(R.string.url_privacy_policy))
          dialogResult.trySuccess(false)
        }
      })
      .setTitle(R.string.app_entry_notifications_dialog_title)
      .setMessage(R.string.app_entry_notifications_dialog_message)
      .setOnDismissListener(new OnDismissListener {
        override def onDismiss(dialog: DialogInterface): Unit = dialogResult.trySuccess(false)
      })
    dialog.show()
    dialogResult.future
  }

  private def onOpenUrl(context: Context, url: String) = {
    try {
      val normUrl = Uri.parse(if (!url.startsWith("http://") && !url.startsWith("https://")) s"http://$url" else url)
      val browserIntent = returning(new Intent(ACTION_VIEW, normUrl))(_.addFlags(FLAG_ACTIVITY_NEW_TASK))
      context.startActivity(browserIntent)
    }
    catch {
      case _: Exception => error(s"Failed to open URL: $url")
    }
  }
}
