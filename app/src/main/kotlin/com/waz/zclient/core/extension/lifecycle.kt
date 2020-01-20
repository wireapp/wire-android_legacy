package com.waz.zclient.core.extension

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver

fun Lifecycle.addObserver(observer: FragmentLifecycleObserver, fragment: Fragment) {
    addObserver(observer as LifecycleObserver)
    observer.from(fragment)
}

fun Lifecycle.addObserver(observer: ActivityLifecycleObserver, activity: AppCompatActivity) {
    addObserver(observer as LifecycleObserver)
    observer.from(activity)
}

interface FragmentLifecycleObserver : LifecycleObserver {
    fun from(owner: Fragment)
}

interface ActivityLifecycleObserver : LifecycleObserver {
    fun from(owner: AppCompatActivity)
}
