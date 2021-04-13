package com.waz.zclient.security.actions

import android.content.Context
import com.waz.zclient.security.SecurityChecklist

import scala.concurrent.Future

class ShowLegalHoldApprovalAction(implicit context: Context) extends SecurityChecklist.Action {

  //TODO: display a pop up
  override def execute(): Future[Unit] = Future.successful(())
}
