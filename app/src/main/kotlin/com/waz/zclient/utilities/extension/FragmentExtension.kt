package com.waz.zclient.utilities.extension


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

inline fun FragmentManager.doTransaction(func: FragmentTransaction.() ->
FragmentTransaction) {
    beginTransaction().func().commit()
}

inline fun <T : Fragment> T.withArgs(
    argsBuilder: Bundle.() -> Unit): T =
    this.apply {
        arguments = Bundle().apply(argsBuilder)
    }

fun Fragment.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    requireActivity().startActivity(intent)
}
