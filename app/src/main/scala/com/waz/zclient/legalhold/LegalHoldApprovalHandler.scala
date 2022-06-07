package com.waz.zclient.legalhold

import androidx.fragment.app.FragmentActivity
import com.waz.model.AccountData.Password
import com.waz.service.AccountsService
import com.waz.sync.handler.LegalHoldError
import com.waz.threading.Threading.Implicits.Ui
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.preferences.DevicesPreferencesUtil
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.{Injectable, Injector, SpinnerController}

import scala.concurrent.{Future, Promise}
import scala.ref.WeakReference

class LegalHoldApprovalHandler(implicit injector: Injector) extends Injectable {

  private lazy val legalHoldController = inject[LegalHoldController]
  private lazy val spinnerController   = inject[SpinnerController]
  private lazy val accountsService     = inject[AccountsService]

  private lazy val actionTaken = Promise[Unit]

  private var activityRef: WeakReference[FragmentActivity] = _

  legalHoldController.legalHoldRequest.onChanged.onUi {
    case None    => dismissDialog()
    case Some(_) =>
  }

  def showDialog(activity: FragmentActivity): Future[Unit] = {
    activityRef = new WeakReference(activity)
    showLegalHoldRequestDialog()
    actionTaken.future
  }

  private def dismissDialog(): Unit = activityRef.get.foreach { activity =>
    legalHoldRequestDialog(activity).foreach(_.dismiss())
    setFinished()
  }

  private def showLegalHoldRequestDialog(showError: Boolean = false): Unit = {
    def showDialog(activity: FragmentActivity,
                   isPassManagedByCompany: Boolean,
                   fingerprint: String): Unit = {
      val fingerprintText = DevicesPreferencesUtil.getFormattedFingerprint(activity, fingerprint).toString

      returning(LegalHoldRequestDialog.newInstance(isPasswordManagedByCompany = isPassManagedByCompany, fingerprintText, showError = showError)) { dialog =>
        dialog.onAccept.onUi(onLegalHoldAccepted)
        dialog.onDecline.onUi(_ => setFinished())
      }.show(activity.getSupportFragmentManager, LegalHoldRequestDialog.TAG)
    }

    activityRef.get.foreach { activity =>
      if (!isShowingLegalHoldRequestDialog(activity)) {

        for {
          request               <- legalHoldController.legalHoldRequest.head
          companyManagedPassword  <- accountsService.activeAccountHasCompanyManagedPassword.head
          fingerprint           <- request match {
                           case Some(r) => legalHoldController.getFingerprint(r)
                           case None    => Future.successful(Option.empty)
                         }
        } yield (request, companyManagedPassword, fingerprint) match {
          case (_, passwordManagedByCompany, Some(fp)) => showDialog(activity, passwordManagedByCompany, fp)
          case (Some(_), _, None) => showGeneralError()
          case _ =>
        }
      }
    }
  }

  private def legalHoldRequestDialog(activity: FragmentActivity) =
    Option(activity.getSupportFragmentManager.findFragmentByTag(LegalHoldRequestDialog.TAG))
      .map(_.asInstanceOf[LegalHoldRequestDialog])

  private def isShowingLegalHoldRequestDialog(activity: FragmentActivity) =
    legalHoldRequestDialog(activity).isDefined

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
