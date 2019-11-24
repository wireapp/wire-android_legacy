package com.waz.zclient.utilities.extension


import android.content.pm.PackageManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

fun AppCompatActivity.getLabel(): String {
    var label = ""
    try {
        val labelRes = packageManager.getActivityInfo(componentName, 0).labelRes
        label = getString(labelRes)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(javaClass.simpleName, e.localizedMessage)
    }
    return label
}

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
