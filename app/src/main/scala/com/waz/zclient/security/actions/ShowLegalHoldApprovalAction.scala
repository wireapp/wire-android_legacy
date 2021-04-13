package com.waz.zclient.security.actions

import android.content.Context
import android.widget.Toast
import com.waz.zclient.security.SecurityChecklist
import com.waz.threading.Threading.Implicits.Ui

import scala.concurrent.Future

class ShowLegalHoldApprovalAction(implicit context: Context) extends SecurityChecklist.Action {

  override def execute(): Future[Unit] = Future {
    Toast.makeText(context, "Please accept legal hold", Toast.LENGTH_SHORT).show()
  }
}
