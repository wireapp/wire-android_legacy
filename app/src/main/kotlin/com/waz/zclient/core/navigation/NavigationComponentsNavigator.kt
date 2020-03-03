package com.waz.zclient.core.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController

internal class NavigationComponentsNavigator(private val navController: NavController) : Navigator {
    override fun navigateTo(navigationId: Int) {
        navController.navigate(navigationId)
    }

    override fun navigateTo(navigationId: Int, bundle: Bundle) {
        navController.navigate(navigationId, bundle)
    }
}

fun Fragment.navigator(): Navigator = NavigationComponentsNavigator(findNavController())
