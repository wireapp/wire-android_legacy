package com.waz.zclient.core.extension

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.zclient.utils.DeprecationUtils
import java.util.*

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

fun Activity.getDeviceLocale(): Locale =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        DeprecationUtils.getDefaultLocale(this)
    } else {
        resources.configuration.locales.get(0)
    }
