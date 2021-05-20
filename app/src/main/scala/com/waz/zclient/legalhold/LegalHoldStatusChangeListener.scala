package com.waz.zclient.legalhold

import android.content.Context
import com.waz.model.ConversationData.LegalHoldStatus
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.threading.Threading._
import com.waz.threading.Threading.Implicits.Ui
import com.waz.zclient.utils.ContextUtils

class LegalHoldStatusChangeListener(implicit injector: Injector) extends Injectable {

  private lazy val legalHoldController       = inject[LegalHoldController]
  private lazy val legalHoldApprovalHandler  = inject[LegalHoldApprovalHandler]
  private lazy val activityLifecycleCallback = inject[ActivityLifecycleCallback]

  legalHoldController.hasPendingRequest.onChanged.onUi {
    case true  => activityLifecycleCallback.withCurrentActivity(legalHoldApprovalHandler.showDialog)
    case false =>
  }

  legalHoldController.legalHoldDisclosureType.onUi {
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

  private def showDisclosureDialog(titleRes: Int, messageRes: Int): Unit =
    activityLifecycleCallback.withCurrentActivity { activity =>
      implicit val context: Context = activity

      ContextUtils.showInfoDialog(
        title = activity.getString(titleRes),
        msg = activity.getString(messageRes)
      ).foreach(_ =>
        legalHoldController.clearLegalHoldDisclosureType
      )
    }
}
