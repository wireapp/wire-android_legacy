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
package com.waz.zclient.preferences.dialogs

import android.app.AlertDialog.Builder
import android.content.DialogInterface
import android.os.Bundle
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.pages.BaseDialogFragment
import com.waz.zclient.preferences.dialogs.FullSyncDialog._
import com.waz.zclient.{FragmentHelper, R}

class FullSyncDialog extends BaseDialogFragment[Container] with FragmentHelper {

  override def onCreateDialog(savedInstanceState: Bundle) = {
    val builder = new Builder(getActivity)
    val inflater = getActivity.getLayoutInflater

    builder
      .setView(inflater.inflate(R.layout.full_sync_dialog, null))
      .setTitle(R.string.pref_advanced_full_sync_title)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        override def onClick(dialog: DialogInterface, which: Int) = {
          import com.waz.threading.Threading.Implicits.Ui
          for {
            zms <- inject[Signal[ZMessaging]].head
            _   <- zms.sync.performFullSync()
          } yield ()
        }
      })
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        override def onClick(dialog: DialogInterface, which: Int) = {
          dismiss()
        }
      })

    builder.create()
  }
}

object FullSyncDialog {
  val Tag: String = getClass.getSimpleName
  trait Container

  def newInstance: FullSyncDialog = new FullSyncDialog
}
