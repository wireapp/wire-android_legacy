package com.waz.zclient.legalhold

import androidx.fragment.app.FragmentActivity
import com.waz.model.AccountData.Password
import com.waz.sync.handler.LegalHoldError
import com.waz.threading.Threading.Implicits.Ui
import com.waz.utils.returning
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.{Injectable, Injector, SpinnerController}

import scala.concurrent.{Future, Promise}
import scala.ref.WeakReference

class LegalHoldApprovalHandler(implicit injector: Injector) extends Injectable {

  private lazy val legalHoldController = inject[LegalHoldController]
  private lazy val spinnerController = inject[SpinnerController]

  private lazy val actionTaken = Promise[Unit]

  private var activityRef : WeakReference[FragmentActivity] = _

  def showDialog(activity: FragmentActivity): Future[Unit] = {
    activityRef = new WeakReference(activity)
    showLegalHoldRequestDialog()
    actionTaken.future
  }

  private def showLegalHoldRequestDialog(showError: Boolean = false): Unit = {
    //TODO: check isSSO and display proper fingerprint
    def showDialog(activity: FragmentActivity): Unit =
      returning(LegalHoldRequestDialog.newInstance(isSso = false, "...", showError = showError)) { dialog =>
        dialog.onAccept(onLegalHoldAccepted)
        dialog.onDecline(_ => setFinished())
      }.show(activity.getSupportFragmentManager, LegalHoldRequestDialog.TAG)

    activityRef.get.foreach { activity =>
      if (!isShowingLegalHoldRequestDialog(activity)) {
        legalHoldController.legalHoldRequest.head.foreach {
          case Some(_) => showDialog(activity)
          case None    =>
        }
      }
    }
  }

  private def isShowingLegalHoldRequestDialog(activity: FragmentActivity) =
    activity.getSupportFragmentManager.findFragmentByTag(LegalHoldRequestDialog.TAG) != null

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

  private def showGeneralError(): Unit = activityRef.get.foreach {
    ContextUtils.showGenericErrorDialog()(_).foreach(_ => setFinished())
  }

  private def setFinished(): Unit = actionTaken.trySuccess(())
}
