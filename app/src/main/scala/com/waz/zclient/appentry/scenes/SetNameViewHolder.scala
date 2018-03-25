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
package com.waz.zclient.appentry.scenes

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.view.View
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient._
import com.waz.zclient.appentry.EntryError
import com.waz.zclient.appentry.controllers.AppEntryController
import com.waz.zclient.appentry.fragments.SignInFragment.{Email, Register, SignInMethod}
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.NameValidator
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

case class SetNameViewHolder(root: View)(implicit val context: Context, eventContext: EventContext, injector: Injector) extends ViewHolder with Injectable {

  private val appEntryController = inject[AppEntryController]

  lazy val inputField = root.findViewById[InputBox](R.id.input_field)

  def onCreate(): Unit = {
    inputField.setValidator(NameValidator)
    inputField.editText.requestFocus()
    inputField.editText.setText(appEntryController.teamUserName)
    inputField.editText.addTextListener(appEntryController.teamUserName = _)
    inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
    KeyboardUtils.showKeyboard(context.asInstanceOf[Activity])
    inputField.setOnClick( text => appEntryController.setName(text).map {
      case Left(error) =>
        Some(getString(EntryError(error.code, error.label, SignInMethod(Register, Email)).bodyResource))
      case _ => None
    } (Threading.Ui))
  }
}
