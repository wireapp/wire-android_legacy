package com.waz.zclient.core.extension


import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment


fun AppCompatActivity.addFragment(frameId: Int, fragment: Fragment) =
    supportFragmentManager.doTransaction { add(frameId, fragment) }

fun AppCompatActivity.replaceFragment(frameId: Int, fragment: Fragment, addToBackStack: Boolean) =
    supportFragmentManager.doTransaction {
        replace(frameId, fragment).apply {
            if (addToBackStack) {
                addToBackStack(fragment.tag)
            }
        }
    }

fun AppCompatActivity.removeFragment(fragment: Fragment) =
    supportFragmentManager.doTransaction { remove(fragment) }
