package com.waz.zclient.core.extension

import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog

fun AlertDialog.Builder.display(
    title: String,
    message: String,
    positiveText: String,
    positiveAction: DialogInterface.OnClickListener,
    negativeText: String? = null,
    negativeAction: DialogInterface.OnClickListener? = null
) {
    val alert = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveText, positiveAction)
    negativeText?.let { alert.setNegativeButton(negativeText, negativeAction) }
    return alert.create().show()
}

fun AlertDialog.Builder.display(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes positiveText: Int,
    positiveAction: DialogInterface.OnClickListener,
    @StringRes negativeText: Int? = null,
    negativeAction: DialogInterface.OnClickListener? = null
) {
    val alert = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveText, positiveAction)
    negativeText?.let { alert.setNegativeButton(negativeText, negativeAction) }
    return alert.create().show()
}
