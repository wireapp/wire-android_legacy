package com.waz.zclient.utilities.extension


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
