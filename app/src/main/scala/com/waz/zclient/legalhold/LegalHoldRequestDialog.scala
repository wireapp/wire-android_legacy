package com.waz.zclient.legalhold

import android.os.Bundle
import com.waz.utils.returning
import com.waz.zclient.preferences.dialogs.ConfirmationWithPasswordDialog
import com.waz.zclient.R

class LegalHoldRequestDialog extends ConfirmationWithPasswordDialog {
  import LegalHoldRequestDialog._

  override lazy val isSSO: Boolean = getArguments.getBoolean(ARG_IS_SSO)

  override lazy val errorMessage: Option[String] = None // TODO

  override lazy val title: String = getString(R.string.legal_hold_request_dialog_title)

  override lazy val message: String = {
    val stringRes =
      if (isSSO) R.string.legal_hold_request_dialog_message_for_sso
      else R.string.legal_hold_request_dialog_message
    getString(stringRes, getArguments.getString(ARG_CLIENT_FINGERPRINT))
  }

  override lazy val positiveButtonText: Int = R.string.legal_hold_request_dialog_positive_button_text

  override lazy val negativeButtonText: Int = R.string.legal_hold_request_dialog_negative_button_text
}

object LegalHoldRequestDialog {
  val TAG = "LegalHoldRequestDialog"

  private val ARG_IS_SSO = "LegalHold_arg_isSso"
  private val ARG_CLIENT_FINGERPRINT = "LegalHold_arg_fingerprint"

  def newInstance(isSso: Boolean, fingerprint: String) : LegalHoldRequestDialog =
    returning(new LegalHoldRequestDialog) {
      _.setArguments(returning(new Bundle()) { args =>
        args.putString(ARG_CLIENT_FINGERPRINT, fingerprint)
        args.putBoolean(ARG_IS_SSO, isSso)
      })
    }
}
