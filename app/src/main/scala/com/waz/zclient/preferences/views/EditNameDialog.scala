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
package com.waz.zclient.preferences.views

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.os.Bundle
import android.text.TextUtils
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.pages.BaseDialogFragment
import com.waz.zclient.preferences.views.EditNameDialog._
import com.waz.zclient.ui.text.TypefaceEditText
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils._
import com.waz.zclient.{FragmentHelper, R}

class EditNameDialog extends BaseDialogFragment[Container] with FragmentHelper {

  private lazy val zms = inject[Signal[ZMessaging]]
  private def editText(dialog: Dialog): Option[TypefaceEditText] = Option(dialog.findViewById(R.id.edit_text).asInstanceOf[TypefaceEditText])

  override def onCreateDialog(savedInstanceState: Bundle) = {

    val initialName = getArguments.getString(NameArg, "")

    val builder = new Builder(getActivity)
    val inflater = getActivity.getLayoutInflater

    builder
      .setView(inflater.inflate(R.layout.edit_name_dialog, null))
      .setTitle(R.string.pref_account_edit_name_title)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        override def onClick(dialog: DialogInterface, which: Int) = {
          val newName = editText(getDialog).fold("")(_.getText.toString.trim)
          if (TextUtils.getTrimmedLength(newName) < getInt(R.integer.account_preferences__min_name_length)(getActivity)) {
            showAlertDialog(
              getActivity, null,
              getString(R.string.pref_account_edit_name_empty_warning),
              getString(R.string.pref_account_edit_name_empty_verify),
              new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, which: Int) = dialog.dismiss()
              }, false)
          } else {
            implicit val ec = Threading.Ui
            for {
              z <- zms.head
              _ <- z.users.updateName(newName)
            } yield {}
            dismiss()
          }
        }
      })
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        override def onClick(dialog: DialogInterface, which: Int) = {
          dismiss()
        }
      })

    returning(builder.create()){ dialog =>
      dialog.setOnShowListener(new OnShowListener {
        override def onShow(dialog: DialogInterface) = {
          editText(dialog.asInstanceOf[Dialog]).foreach{ editText =>
            editText.setText(initialName)
            editText.setSelection(initialName.length)
            editText.callOnClick()
          }
        }
      })
    }
  }
}

object EditNameDialog {
  val Tag = implicitLogTag
  trait Container

  private val NameArg = "NameArg"

  def newInstance(name: String): EditNameDialog = {
    val bundle = new Bundle()
    val fragment = new EditNameDialog
    bundle.putString(NameArg, name)
    fragment.setArguments(bundle)
    fragment
  }
}
