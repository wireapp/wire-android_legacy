@file:JvmName("FragmentUtils")
package com.waz.zclient.utils.extensions

import android.support.v4.app.Fragment

/**
 * Returns the latest child fragment that is added to this fragment.
 *
 * Note: The child fragment must be added to the backstack by setting
 * [android.app.FragmentTransaction.addToBackStack] w/ a non-null tag.
 *
 * @return the topmost fragment in the child fragment stack.
 */
fun Fragment.getTopMostFragment() : Fragment? {
    childFragmentManager.run {
        return when (backStackEntryCount) {
            0 -> null
            else -> findFragmentByTag(getBackStackEntryAt(backStackEntryCount - 1).name)
        }
    }
}
