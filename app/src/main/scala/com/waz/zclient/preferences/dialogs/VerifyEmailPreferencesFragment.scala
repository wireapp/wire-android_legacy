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
import android.support.v4.app.DialogFragment
import android.support.v4.app.DialogFragment.STYLE_NO_FRAME
import android.text.TextUtils
import android.view.{LayoutInflater, View, ViewGroup, WindowManager}
import android.widget.TextView
import com.waz.model.EmailAddress
import com.waz.service.ZMessaging
import com.waz.utils.returning
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.DeprecationUtils
import com.waz.zclient.{FragmentHelper, R}

object VerifyEmailPreferencesFragment {

  val ARG_EMAIL: String = "ARG_EMAIL"
  val Tag: String = com.waz.ZLog.ImplicitTag.implicitLogTag

  def apply(email: EmailAddress): VerifyEmailPreferencesFragment = {
    val fragment = new VerifyEmailPreferencesFragment
    val arg = new Bundle()
    arg.putString(ARG_EMAIL, email.str)
    fragment.setArguments(arg)
    fragment
  }
}

class VerifyEmailPreferencesFragment extends DialogFragment with FragmentHelper {

  import VerifyEmailPreferencesFragment.ARG_EMAIL

  private lazy val email = TextUtils.htmlEncode(getStringArg(ARG_EMAIL).getOrElse(""))

  private lazy val resendButton = view[TextView](R.id.tv__resend_button)

  returning(view[TypefaceTextView](R.id.tv__back_button)) { vh =>
    vh.onClick { _ =>
      dismiss()
    }
  }

  returning(view[TypefaceTextView](R.id.tv__change_email_button)) { vh =>
    vh.onClick { _ =>
      dismiss()
    }
  }

  resendButton.onClick { _ =>
    ZMessaging.currentAccounts.requestVerificationEmail(EmailAddress(email))
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Theme_Dark_Preferences)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.fragment_preference_email_verification, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    super.onViewCreated(view, savedInstanceState)

    resendButton.foreach(_.setText(DeprecationUtils.fromHtml(getString(R.string.pref__account_action__email_verification__resend, email))))
  }

}
