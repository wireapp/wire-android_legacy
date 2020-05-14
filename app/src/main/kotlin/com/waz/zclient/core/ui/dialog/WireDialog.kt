package com.waz.zclient.core.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.waz.zclient.R

class WireDialog {

    class Builder(context: Context) {
        private val builder = AlertDialog.Builder(context)

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
            builder.setTitle(resId)
            return this
        }

        fun title(value: String): Builder {
            builder.setTitle(value)
            return this
        }

        fun message(@StringRes resId: Int): Builder {
            builder.setMessage(resId)
            return this
        }


        fun message(value: String): Builder {
            builder.setMessage(value)
            return this
        }

        fun positiveButton(@StringRes resId: Int, listener: ((DialogInterface, Int) -> Unit)): Builder {
            builder.setPositiveButton(resId, listener)
            return this
        }

        fun positiveButton(text: String, listener: ((DialogInterface, Int) -> Unit)): Builder {
            builder.setPositiveButton(text, listener)
            return this
        }

        fun negativeButton(@StringRes resId: Int, listener: ((DialogInterface, Int) -> Unit)): Builder {
            builder.setNegativeButton(resId, listener)
            return this
        }

        fun negativeButton(text: String, listener: ((DialogInterface, Int) -> Unit)): Builder {
            builder.setNegativeButton(text, listener)
            return this
        }

        fun show() {
            builder.create().show()
        }

    }

    companion object {
        const val GENERIC_ERROR = 0
        const val NETWORK_CONNECTION_ERROR = 1
    }
}
