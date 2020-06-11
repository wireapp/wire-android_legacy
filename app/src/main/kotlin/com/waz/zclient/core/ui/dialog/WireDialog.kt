package com.waz.zclient.core.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface

sealed class DialogType(val block: AlertDialog.Builder.() -> Unit)
internal class GenericError(errorMessage: String) : DialogType({
    setTitle(errorMessage)
    setPositiveButton("Ok", DialogOwner.NO_OP_LISTENER)
})

interface DialogOwner {
    companion object {
        val NO_OP_LISTENER = DialogInterface.OnClickListener { _, _ -> /*no-op*/ }
    }

    fun showDialog(context: Context, block: AlertDialog.Builder.() -> Unit) {
        AlertDialog.Builder(context).apply(block).create().show()
    }

    fun showDialog(context: Context, type: DialogType) {
        AlertDialog.Builder(context).apply(type.block).create().show()
    }

    fun createDialog(context: Context, block: AlertDialog.Builder.() -> Unit) =
        AlertDialog.Builder(context).apply(block).create()

    fun createDialog(context: Context, type: DialogType) =
        AlertDialog.Builder(context).apply(type.block).create()

    fun showErrorDialog(context: Context, errorMessage: String) =
        showDialog(context, GenericError(errorMessage).block)
}
