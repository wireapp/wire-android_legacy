package com.waz.zclient.legalhold

import android.app.Dialog
import android.os.Bundle
import com.waz.utils.returning
import com.waz.zclient.preferences.dialogs.ConfirmationWithPasswordDialog
import com.waz.zclient.R

class LegalHoldRequestDialog extends ConfirmationWithPasswordDialog {
  import LegalHoldRequestDialog._

  override lazy val isPasswordManagedByCompany: Boolean = getArguments.getBoolean(ARG_IS_PASSWORD_COMPANY_MANAGED)

  override lazy val errorMessage: Option[String] =
    if (getArguments.getBoolean(ARG_SHOW_ERROR))
      Some(getString(R.string.legal_hold_request_dialog_wrong_password_error))
    else None

  override lazy val title: String = getString(R.string.legal_hold_request_dialog_title)

  override lazy val message: String = {
    val stringRes =
      if (isPasswordManagedByCompany) R.string.legal_hold_request_dialog_message_for_sso
      else R.string.legal_hold_request_dialog_message
    getString(stringRes, getArguments.getString(ARG_CLIENT_FINGERPRINT))
  }

  override lazy val positiveButtonText: Int = R.string.legal_hold_request_dialog_positive_button_text

  override lazy val negativeButtonText: Int = R.string.legal_hold_request_dialog_negative_button_text

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val dialog = super.onCreateDialog(savedInstanceState)
    setCancelable(false)
    dialog
  }
}

object LegalHoldRequestDialog {
  val TAG = "LegalHoldRequestDialog"

  private val ARG_IS_PASSWORD_COMPANY_MANAGED = "LegalHold_arg_isPasswordCompanyManaged"
  private val ARG_CLIENT_FINGERPRINT = "LegalHold_arg_fingerprint"
  private val ARG_SHOW_ERROR = "LegalHold_arg_showError"

  def newInstance(isPasswordManagedByCompany: Boolean, fingerprint: String, showError: Boolean) : LegalHoldRequestDialog =
    returning(new LegalHoldRequestDialog) {
      _.setArguments(returning(new Bundle()) { args =>
        args.putString(ARG_CLIENT_FINGERPRINT, fingerprint)
        args.putBoolean(ARG_IS_PASSWORD_COMPANY_MANAGED, isPasswordManagedByCompany)
        args.putBoolean(ARG_SHOW_ERROR, showError)
      })
    }
}
