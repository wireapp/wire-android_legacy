package com.waz.zclient.core.ui.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.waz.zclient.R

object Alert {
    fun showError(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    fun showNetworkConnectionError(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.no_internet_connection_title)
            .setMessage(R.string.no_internet_connection_message)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }
}
