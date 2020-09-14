package com.waz.zclient.core.extension

import android.app.Activity
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

fun AppCompatActivity.addFragment(frameId: Int, fragment: Fragment) =
    supportFragmentManager.doTransaction { add(frameId, fragment) }

fun AppCompatActivity.replaceFragment(frameId: Int, fragment: Fragment, addToBackStack: Boolean = true) =
    supportFragmentManager.doTransaction {
        replace(frameId, fragment).apply {
            if (addToBackStack) {
                addToBackStack(fragment.tag)
            }
        }
    }

fun AppCompatActivity.removeFragment(fragment: Fragment) =
    supportFragmentManager.doTransaction { remove(fragment) }

fun Activity.getDeviceLocale() =
    resources.configuration.locales.get(0)

fun Activity.hideKeyboard() {
    val cf = currentFocus
    if (cf != null) {
        val inputMethodManager = this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(cf.windowToken, 0)
    }
}

fun Activity.showKeyboard() {
    val inputMethodManager = this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY)
}
