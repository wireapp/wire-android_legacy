@file:JvmName("BottomNavigationUtil")

package com.waz.zclient.utils.extensions

import android.annotation.SuppressLint
import android.support.design.internal.BottomNavigationMenuView
import android.support.design.widget.BottomNavigationView
import android.support.design.internal.BottomNavigationItemView
import timber.log.Timber

@SuppressLint("RestrictedApi")
//TODO: Use app:labelVisibilityMode="unlabeled" when support lib updated to 28.x
// & delete relevant proguard rule
fun BottomNavigationView.disableShiftMode() {
    val menuView: BottomNavigationMenuView = getChildAt(0) as BottomNavigationMenuView
    try {
        menuView.javaClass.getDeclaredField("mShiftingMode").let {
            it.isAccessible = true
            it.setBoolean(menuView, false)
            it.isAccessible = false
        }

        for (i in 0 until menuView.childCount) {
            val item = menuView.getChildAt(i) as BottomNavigationItemView
            item.setShiftingMode(false)
            // set once again checked value, so view will be updated
            item.setChecked(item.itemData.isChecked)
        }
    } catch (e: NoSuchFieldException) {
        Timber.e(e, "Unable to get shift mode field")
    } catch (e: IllegalAccessException) {
        Timber.e(e, "Unable to change value of shift mode")
    }

}
