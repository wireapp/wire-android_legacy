package com.waz.zclient.security.actions

import android.content.Context
import androidx.fragment.app.{FragmentActivity, FragmentManager}
import com.waz.model.AccountData.Password
import com.waz.sync.handler.LegalHoldError
import com.waz.threading.Threading.Implicits.Ui
import com.waz.utils.returning
import com.waz.zclient.SpinnerController
import com.waz.zclient.legalhold.{LegalHoldController, LegalHoldRequestDialog}
import com.waz.zclient.security.SecurityChecklist
import com.waz.zclient.utils.ContextUtils

import scala.concurrent.{Future, Promise}
import scala.util.Try

class ShowLegalHoldApprovalAction(legalHoldController: LegalHoldController,
                                  spinnerController: SpinnerController)(implicit context: Context)
  extends SecurityChecklist.Action {

  private lazy val actionTaken = Promise[Unit]

  override def execute(): Future[Unit] = {
    showLegalHoldRequestDialog()
    actionTaken.future
  }

  private def showLegalHoldRequestDialog(showError: Boolean = false): Unit = {
    //TODO: check isSSO and display proper fingerprint
    def showDialog(fragmentManager: FragmentManager): Unit =
      returning(LegalHoldRequestDialog.newInstance(isSso = false, "...", showError = showError)) { dialog =>
        dialog.onAccept(onLegalHoldAccepted)
        dialog.onDecline(_ => setFinished())
      }.show(fragmentManager, LegalHoldRequestDialog.TAG)

    activity.map(_.getSupportFragmentManager).foreach { fragmentManager =>
      if (!isShowingLegalHoldRequestDialog(fragmentManager)) {
        legalHoldController.legalHoldRequest.head.foreach {
          case Some(_) => showDialog(fragmentManager)
          case None    =>
        }
      }
    }
  }

  private def isShowingLegalHoldRequestDialog(fragmentManager: FragmentManager) =
    fragmentManager.findFragmentByTag(LegalHoldRequestDialog.TAG) != null

  private def onLegalHoldAccepted(password: Option[Password]): Unit = {
    spinnerController.showDimmedSpinner(show = true)

    legalHoldController.approveRequest(password).map { result =>
      spinnerController.hideSpinner()
      result
    }.foreach {
      case Left(LegalHoldError.InvalidPassword) => showLegalHoldRequestDialog(true)
      case Left(_)  => showGeneralError()
      case Right(_) => setFinished()
    }
  }

  private def showGeneralError(): Unit =
    ContextUtils.showGenericErrorDialog().foreach(_ => setFinished())

  private def setFinished(): Unit = actionTaken.trySuccess(())

  private def activity = Try(context.asInstanceOf[FragmentActivity])
}
