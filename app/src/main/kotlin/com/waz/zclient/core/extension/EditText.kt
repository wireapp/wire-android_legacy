package com.waz.zclient.core.extension

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

//TODO debating whether this is a correct use-case for an extension function
fun EditText.textChanged(beforeFunc: (String) -> Unit,
                         afterFunc: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            afterFunc(s.toString())
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            beforeFunc(s.toString())
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}
