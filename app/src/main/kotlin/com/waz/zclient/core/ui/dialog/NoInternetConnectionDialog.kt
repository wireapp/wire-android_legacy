package com.waz.zclient.core.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.waz.zclient.R
import com.waz.zclient.core.extension.display

class NoInternetConnectionAlertDialog {
    companion object {

        fun show(context: Context) {
            AlertDialog.Builder(context).display(
                title = R.string.no_internet_connection_title,
                message = R.string.no_internet_connection_message,
                positiveText = R.string.no_internet_connection_ok,
                positiveAction = DialogInterface.OnClickListener { dialog, _ -> dialog.dismiss() }
            )
        }
    }
}