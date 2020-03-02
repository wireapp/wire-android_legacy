package com.waz.zclient.core.navigation

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController

internal class NavigationComponentsNavigator(private val navController: NavController) : Navigator {
    override fun navigateTo(navigationId: Int) {
        navController.navigate(navigationId)
    }
}

fun Fragment.navigator(): Navigator = NavigationComponentsNavigator(findNavController())
