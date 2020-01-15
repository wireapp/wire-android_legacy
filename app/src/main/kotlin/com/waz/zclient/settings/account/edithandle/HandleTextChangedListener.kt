package com.waz.zclient.settings.account.edithandle

import android.text.TextWatcher

//Temporary naming. Needs to reconsider the name.
interface HandleTextChangedListener : TextWatcher {
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}
