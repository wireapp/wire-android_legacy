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

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.waz.ZLog
import com.waz.content.GlobalPreferences
import com.waz.model.{ConfirmationCode, EmailAddress}
import com.waz.service.GlobalModule
import com.waz.threading.Threading
import com.waz.zclient._
import com.waz.zclient.appentry.CreateTeamFragment
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.NumberCodeInput
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

import scala.concurrent.Future

case class VerifyTeamEmailFragment() extends CreateTeamFragment{

  private lazy val codeField = view[NumberCodeInput](R.id.input_field)
  private lazy val resend = view[TypefaceTextView](R.id.resend_email)
  private lazy val changeEmail = view[TypefaceTextView](R.id.change_email)
  private lazy val subtitle = view[TypefaceTextView](R.id.subtitle)

  private var resendCount = 0
  @volatile private var cooldown = true

  override val layoutId: Int = R.layout.verify_email_scene

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    import Threading.Implicits.Ui

    subtitle.foreach(_.setText(getString(R.string.teams_verify_email_subtitle, createTeamController.teamEmail)))

    codeField.foreach { codeField =>
      codeField.requestInputFocus()
      if (createTeamController.code.nonEmpty)
        codeField.inputCode(createTeamController.code)
      codeField.codeText.onUi { code => createTeamController.code = code._1 }
      KeyboardUtils.showKeyboard(context.asInstanceOf[Activity])
      codeField.setOnCodeSet({ case (code, copyPaste) =>
        (for {
          _ <- setButtonsVisible(false)
          resp <- accountsService.verifyEmailAddress(EmailAddress(createTeamController.teamEmail), ConfirmationCode(code))
          _ <- setButtonsVisible(true)
        } yield resp).flatMap {
          case Left(error) =>
            Future.successful(Some(getString(EmailError(error).bodyResource)))
          case _ =>
            inject[GlobalModule].prefs(GlobalPreferences.ShowMarketingConsentDialog).apply().flatMap {
              case true => showConfirmationDialogWithNeutralButton(
                R.string.receive_news_and_offers_request_title,
                R.string.receive_news_and_offers_request_body,
                R.string.app_entry_dialog_privacy_policy,
                R.string.app_entry_dialog_accept,
                R.string.app_entry_dialog_not_now
              )
              case false => Future.successful(Some(false))
            }.map { confirmed =>
              createTeamController.receiveNewsAndOffers = confirmed
              createTeamController.code = code
              showFragment(SetNameFragment(), SetNameFragment.Tag)
              if (confirmed.isEmpty) inject[BrowserController].openUrl(getString(R.string.url_privacy_policy))
              None
            }
        }
      })
    }

    resend.foreach(_.onClick {
      if (resendCount < 3) {
        if (cooldown) {
          resendCount += 1
          cooldown = false
          accountsService.requestEmailCode(EmailAddress(createTeamController.teamEmail)) map { resp =>
            cooldown = true
            resp match {
              case Left(err) => showErrorDialog(EmailError(err))
              case Right(_)  => showToast(R.string.app_entry_email_sent)
            }
          }
        }
      } else {
        showToast(R.string.too_many_attempts_header)
      }
    })

    changeEmail.foreach(_.onClick(onBackPressed()))
  }

  def setButtonsVisible(visible: Boolean): Future[Unit] = Future.successful {
    resend.foreach(_.setVisibility(if (visible) View.VISIBLE else View.INVISIBLE))
    changeEmail.foreach(_.setVisibility(if (visible) View.VISIBLE else View.INVISIBLE))
  }
}

object VerifyTeamEmailFragment {
  val Tag: String = ZLog.ImplicitTag.implicitLogTag
}
