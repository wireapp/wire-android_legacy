package com.waz.zclient.legalhold

import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{Injectable, Injector}


class LegalHoldStatusChangeListener(implicit injector: Injector) extends Injectable {

  private lazy val legalHoldController       = inject[LegalHoldController]
  private lazy val legalHoldApprovalHandler  = inject[LegalHoldApprovalHandler]
  private lazy val activityLifecycleCallback = inject[ActivityLifecycleCallback]

  legalHoldController.hasPendingRequest.onChanged {
    case true  => activityLifecycleCallback.withCurrentActivity(legalHoldApprovalHandler.showDialog)
    case false =>
  }

  //TODO: show "LH no longer active" pop up when LH is disabled
}
