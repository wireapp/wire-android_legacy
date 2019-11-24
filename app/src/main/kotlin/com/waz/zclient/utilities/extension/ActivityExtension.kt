package com.waz.zclient.utilities.extension


import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment


fun AppCompatActivity.addFragment(frameId: Int, fragment: Fragment) {
    supportFragmentManager.doTransaction { add(frameId, fragment) }
}

fun AppCompatActivity.replaceFragment(frameId: Int, fragment: Fragment, addToBackStack: Boolean) {
    when (addToBackStack) {
        true -> supportFragmentManager.doTransaction { replace(frameId, fragment).addToBackStack(fragment.tag) }
        false -> supportFragmentManager.doTransaction { replace(frameId, fragment) }
    }
}

fun AppCompatActivity.removeFragment(fragment: Fragment) {
    supportFragmentManager.doTransaction { remove(fragment) }
}
