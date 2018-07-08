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
import android.support.design.widget.TextInputLayout
import android.support.v4.app.DialogFragment
import android.support.v4.app.DialogFragment.STYLE_NO_FRAME
import android.text.{Editable, TextUtils}
import android.view.View.OnKeyListener
import android.view.WindowManager.LayoutParams.{SOFT_INPUT_ADJUST_RESIZE, SOFT_INPUT_STATE_ALWAYS_HIDDEN}
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.{EditText, TextView}
import com.waz.model.{ConfirmationCode, PhoneNumber}
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.pages.main.profile.views.OnTextChangedListener
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.{DeprecationUtils, RichView}
import com.waz.zclient.{FragmentHelper, R}

class VerifyPhoneFragment extends DialogFragment with FragmentHelper {
  import VerifyPhoneFragment._

  private val verificationCode = new Array[Char](CodeLength)
  private var textBoxes = Seq.empty[EditText]
  private lazy val accountsService = inject[AccountsService]

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    verificationCode.indices.foreach { i =>
      verificationCode(i) = ' '
    }
    setStyle(STYLE_NO_FRAME, R.style.Theme_Dark_Preferences)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    getDialog.getWindow.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE | SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    super.onViewCreated(view, savedInstanceState)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {

    val phoneNumber = PhoneNumber(DeprecationUtils.formatNumber(getArguments.getString(ArgPhone, "")))

    val view = inflater.inflate(R.layout.fragment_preference_phone_number_verification, container, false)

    val verificationCodeInputLayout = returning(findById[TextInputLayout](view, R.id.til__verification_code))(_.setErrorEnabled(true))

    returning(findById[View](view, R.id.tv__back_button)) (_.onClick(dismiss()))

    val okButton = returning(findById[View](view, R.id.tv__ok_button)) { v =>
      v.setEnabled(false)
      v.onClick {
        accountsService.verifyPhoneNumber(phoneNumber, ConfirmationCode(new String(verificationCode)), dryRun = false).map {
          case Right(()) => dismiss()
          case _ => verificationCodeInputLayout.setError(getString(R.string.generic_error_header))
        }(Threading.Ui)
      }
    }

    returning(findById[TextView](view, R.id.tv__change_number_button))(_.onClick(dismiss()))


    returning(findById[TextView](view, R.id.tv__resend_button)) { v =>
      v.onClick {
        v.animate.alpha(0f).start()

        accountsService.requestPhoneCode(phoneNumber, login = false).map { res =>
          v.animate.alpha(1f).start()
          res.left.foreach { _ =>
            verificationCodeInputLayout.setError(getString(R.string.generic_error_header))
          }
        }(Threading.Ui)
      }
    }

    returning(findById[TextView](view, R.id.tv__verification_description)) {
      _.setText(getString(R.string.pref__account_action__phone_verification__description, phoneNumber.str))
    }

    textBoxes = Seq(
      R.id.et__verification_code__1,
      R.id.et__verification_code__2,
      R.id.et__verification_code__3,
      R.id.et__verification_code__4,
      R.id.et__verification_code__5,
      R.id.et__verification_code__6
    ).map(id => findById[EditText](view, id))

    textBoxes.zipWithIndex.foreach { case (tb, i) =>

      def moveToNextFreeTextBox(): Unit = {
        val value = tb.getText.toString.trim
        val c = if (TextUtils.isEmpty(value)) ' ' else value.charAt(0)

        verificationCode(i) = c
        val jumpTo = {
          val nextIndex = verificationCode.indexWhere(_ == ' ')
          if (nextIndex == -1) -1
          else {
            nextIndex - (if (c == ' ' && nextIndex > 0) 1 else 0)
          }
        }

        if (jumpTo == -1) {
          okButton.setEnabled(true)
          if (i == textBoxes.size) okButton.requestFocus
        } else {
          okButton.setEnabled(false)
          textBoxes(jumpTo).requestFocus
        }
      }

      tb.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__bold)))
      tb.addTextChangedListener(new OnTextChangedListener() {
        override def afterTextChanged(s: Editable) = moveToNextFreeTextBox()
      })
      tb.setOnKeyListener(new OnKeyListener {
        override def onKey(v: View, keyCode: Int, event: KeyEvent) = {
          //TODO this may not work on all devices... generally using key events only works for hard keyboards, but this should
          //be okay for this edge case. A better solution would be to have just one styled edit text.
          if (keyCode == KeyEvent.KEYCODE_DEL)
            moveToNextFreeTextBox()
          false
        }
      })
    }
    view
  }
}

object VerifyPhoneFragment {

  val TAG = classOf[VerifyPhoneFragment].getSimpleName
  private val CodeLength = 6
  private val ArgPhone = "ARG_PHONE"

  def newInstance(phoneNumber: String): VerifyPhoneFragment =
    returning(new VerifyPhoneFragment) {
      _.setArguments(returning(new Bundle) { b =>
        b.putString(ArgPhone, phoneNumber)
      })
    }
}
