package com.waz.zclient.core.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.waz.zclient.R

class WireDialog {

    class Builder(context: Context) {
        private val dialogBuilder = AlertDialog.Builder(context)

        fun type(value: Int): Builder {

            when (value) {
                GENERIC_ERROR -> {
                    positiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                }
                NETWORK_CONNECTION_ERROR -> {
                    title(R.string.no_internet_connection_title)
                    message(R.string.no_internet_connection_message)
                    positiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                }
            }
            return this
        }

        fun title(@StringRes resId: Int): Builder {
            dialogBuilder.setTitle(resId)
            return this
        }

        fun title(value: String): Builder {
            dialogBuilder.setTitle(value)
            return this
        }

        fun message(@StringRes resId: Int): Builder {
            dialogBuilder.setMessage(resId)
            return this
        }

        fun message(value: String): Builder {
            dialogBuilder.setMessage(value)
            return this
        }

        fun positiveButton(@StringRes resId: Int, listener: ((DialogInterface, Int) -> Unit)): Builder {
            dialogBuilder.setPositiveButton(resId, listener)
            return this
        }

        fun positiveButton(text: String, listener: ((DialogInterface, Int) -> Unit)): Builder {
            dialogBuilder.setPositiveButton(text, listener)
            return this
        }

        fun negativeButton(@StringRes resId: Int, listener: ((DialogInterface, Int) -> Unit)): Builder {
            dialogBuilder.setNegativeButton(resId, listener)
            return this
        }

        fun negativeButton(text: String, listener: ((DialogInterface, Int) -> Unit)): Builder {
            dialogBuilder.setNegativeButton(text, listener)
            return this
        }

        fun show() {
            dialogBuilder.create().show()
        }
    }

    companion object {
        const val GENERIC_ERROR = 0
        const val NETWORK_CONNECTION_ERROR = 1
    }
}
