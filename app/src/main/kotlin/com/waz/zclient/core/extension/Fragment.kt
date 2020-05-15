package com.waz.zclient.core.extension

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

inline fun FragmentManager.doTransaction(func: FragmentTransaction.() -> FragmentTransaction) =
    beginTransaction().func().commit()

inline fun <T : Fragment> T.withArgs(argsBuilder: Bundle.() -> Unit): T =
    this.apply {
        arguments = Bundle().apply(argsBuilder)
    }

fun Fragment.openUrl(url: String) =
    requireActivity().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

fun Fragment.startActivityWithAction(intentAction: String) =
    startActivity(Intent().apply { action = intentAction })

fun Fragment.replaceFragment(frameId: Int, fragment: Fragment, addToBackStack: Boolean = true) {
    (activity as AppCompatActivity).replaceFragment(frameId, fragment, addToBackStack)
}

fun Fragment.showKeyboard() = activity?.let { it.showKeyboard() }

fun Fragment.hideKeyboard() = activity?.let { it.hideKeyboard() }
