package com.waz.zclient.core.extension

import android.content.ComponentCallbacks
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import org.koin.android.ext.android.getKoin
import org.koin.android.viewmodel.ext.android.getViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named

fun LifecycleOwner.getKoin() = (this as ComponentCallbacks).getKoin()

fun LifecycleOwner.createScope(
    scopeId: String,
    scopeName: String
) = getKoin().getOrCreateScope(scopeId, named(scopeName))

/**
 * Lazy getByClass a viewModel instance
 *
 * @param scopeId - Is used to find the scope in reference for the ViewModel you're trying to retrieve
 * @param viewModelQualifier - Koin BeanDefinition qualifier (if have several ViewModel beanDefinition of the same type)
 * @param parameters - parameters to pass to the BeanDefinition
 */
inline fun <reified T : ViewModel> LifecycleOwner.viewModel(
    scopeId: String,
    viewModelQualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): Lazy<T> = lazy {
    getKoin().getScope(scopeId)
        .getViewModel(this, T::class, viewModelQualifier, parameters)
}
