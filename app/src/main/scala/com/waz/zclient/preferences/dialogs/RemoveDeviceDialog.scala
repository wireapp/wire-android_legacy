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
import com.waz.utils.returning
import com.waz.zclient.R

class RemoveDeviceDialog extends ConfirmationWithPasswordDialog {
  import RemoveDeviceDialog._

  override lazy val isPasswordManagedByCompany: Boolean = getArguments.getBoolean(IsSSOARG)

  override lazy val errorMessage: Option[String] = Option(getArguments.getString(ErrorArg))

  override lazy val title: String = getString(
    R.string.otr__remove_device__title,
    getArguments.getString(NameArg, getString(R.string.otr__remove_device__default))
  )

  override lazy val message: String = {
    val resId = if (isPasswordManagedByCompany) R.string.otr__remove_device__are_you_sure else R.string.otr__remove_device__message
    getString(resId)
  }

  override lazy val positiveButtonText: Int = R.string.otr__remove_device__button_delete

  override lazy val negativeButtonText: Int = R.string.otr__remove_device__button_cancel
}

object RemoveDeviceDialog {
  val FragmentTag = RemoveDeviceDialog.getClass.getSimpleName
  private val NameArg  = "ARG_NAME"
  private val ErrorArg = "ARG_ERROR"
  private val IsSSOARG = "ARG_IS_SSO"

  def newInstance(deviceName: String, error: Option[String], isSSO: Boolean): RemoveDeviceDialog =
    returning(new RemoveDeviceDialog) {
      _.setArguments(returning(new Bundle()) { b =>
        b.putString(NameArg, deviceName)
        error.foreach(b.putString(ErrorArg, _))
        b.putBoolean(IsSSOARG, isSSO)
      })
    }

}
