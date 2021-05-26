package com.waz.zclient.legalhold

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.waz.model.ConversationData.LegalHoldStatus
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.threading.Threading._

class LegalHoldStatusChangeListener(implicit injector: Injector) extends Injectable {

  private lazy val legalHoldController       = inject[LegalHoldController]
  private lazy val legalHoldApprovalHandler  = inject[LegalHoldApprovalHandler]
  private lazy val activityLifecycleCallback = inject[ActivityLifecycleCallback]

  private var dialog : Option[AlertDialog] = None

  legalHoldController.hasPendingRequest.onChanged.onUi {
    case true  => dialog.foreach(_.dismiss())
                  dialog = None
                  activityLifecycleCallback.withCurrentActivity(legalHoldApprovalHandler.showDialog)
    case false =>
  }

  legalHoldController.legalHoldDisclosureType.onUi { status =>
    dialog.foreach(_.dismiss())
    dialog = None

    status match {
      case Some(LegalHoldStatus.Enabled)  =>
        showDisclosureDialog(
          R.string.legal_hold_activated_dialog_title,
          R.string.legal_hold_self_user_info_message)
      case Some(LegalHoldStatus.Disabled) =>
        showDisclosureDialog(
          R.string.legal_hold_deactivated_dialog_title,
          R.string.legal_hold_deactivated_dialog_message)
      case _                              =>
    }
  }

  private def showDisclosureDialog(titleRes: Int, messageRes: Int): Unit =
    activityLifecycleCallback.withCurrentActivity { activity =>

      val alertDialog = new AlertDialog.Builder(activity)
        .setTitle(titleRes)
        .setMessage(messageRes)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int): Unit = {
            LegalHoldStatusChangeListener.this.dialog = None
            legalHoldController.clearLegalHoldDisclosureType
          }
        })
        .setCancelable(false)
        .create()

      dialog = Some(alertDialog)
      alertDialog.show()
    }
}
