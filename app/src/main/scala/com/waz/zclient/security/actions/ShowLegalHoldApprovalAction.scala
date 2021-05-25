package com.waz.zclient.security.actions

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.waz.zclient.legalhold.LegalHoldApprovalHandler
import com.waz.zclient.security.SecurityChecklist

import scala.concurrent.Future
import scala.util.Try

class ShowLegalHoldApprovalAction(legalHoldApprovalHandler: LegalHoldApprovalHandler)(implicit context: Context)
  extends SecurityChecklist.Action {

  override def execute(): Future[Unit] =
    Try(context.asInstanceOf[FragmentActivity])
      .toOption
      .fold(Future.successful(()))(legalHoldApprovalHandler.showDialog)
}
