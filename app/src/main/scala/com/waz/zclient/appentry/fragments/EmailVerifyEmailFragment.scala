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
package com.waz.zclient.appentry.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.zclient.appentry.controllers.AppEntryController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.utils.{KeyboardUtils, TextViewUtils}
import com.waz.zclient.{FragmentHelper, R}
import com.waz.ZLog.ImplicitTag._

object EmailVerifyEmailFragment {
  val TAG: String = classOf[EmailVerifyEmailFragment].getName

  def newInstance: Fragment = new EmailVerifyEmailFragment

  trait Container

}

class EmailVerifyEmailFragment extends BaseFragment[EmailVerifyEmailFragment.Container] with FragmentHelper with View.OnClickListener {

  private lazy val resendTextView = view[TextView](R.id.ttv__pending_email__resend)
  private lazy val checkEmailTextView = view[TextView](R.id.ttv__sign_up__check_email)
  private lazy val didntGetEmailTextView = view[TextView](R.id.ttv__sign_up__didnt_get)
  private lazy val backButton = view[View](R.id.ll__activation_button__back)

  lazy val appEntryController = inject[AppEntryController]

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {

    findById[View](view, R.id.gtv__not_now__close).setVisibility(View.GONE)
    findById[View](view, R.id.fl__confirmation_checkmark).setVisibility(View.GONE)
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    //TODO
//    appEntryController.currentAccount.map(_.flatMap(acc => acc.pendingEmail.orElse(acc.email))).onUi {
//      case Some(email) => setEmailText(email.str)
//      case _ => setEmailText("")
//    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_pending_email_email_confirmation, viewGroup, false)
  }

  override def onResume(): Unit = {
    super.onResume()
    backButton.setOnClickListener(this)
    resendTextView.setOnClickListener(this)
  }

  override def onPause(): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    backButton.setOnClickListener(null)
    resendTextView.setOnClickListener(null)
    super.onPause()
  }

  private def setEmailText(email: String): Unit = {
    checkEmailTextView.setText(getResources.getString(R.string.profile__email__verify__instructions, email))
    TextViewUtils.boldText(checkEmailTextView)
  }

  private def back(): Unit = {
    import com.waz.threading.Threading.Implicits.Background
//    appEntryController.currentAccount.map(_.map(_.phone.isDefined)).head.foreach { //TODO
//      case Some(true) =>
//        appEntryController.cancelEmailVerification()
//      case _ =>
//        appEntryController.removeCurrentAccount()
//    }
  }

  def onClick(v: View): Unit = {
    v.getId match {
      case R.id.ll__activation_button__back =>
        back()
      case R.id.ttv__pending_email__resend =>
        didntGetEmailTextView.animate.alpha(0).start()
        resendTextView.animate.alpha(0).withEndAction(new Runnable() {
          def run(): Unit = {
            resendTextView.setEnabled(false)
          }
        }).start()
        appEntryController.resendActivationEmail()
    }
  }

  override def onBackPressed(): Boolean = {
    back()
    true
  }
}
