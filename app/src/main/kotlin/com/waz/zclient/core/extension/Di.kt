package com.waz.zclient.core.extension

import android.content.ComponentCallbacks
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import org.koin.android.ext.android.getKoin
import org.koin.android.viewmodel.ext.android.getViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named

inline fun <reified T : ViewModel> LifecycleOwner.viewModel(
    scopeId: String,
    viewModelQualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): Lazy<T> = lazy {
    getKoin().getScope(scopeId)
        .getViewModel(this, T::class, viewModelQualifier, parameters)
}

inline fun <reified T : ViewModel> LifecycleOwner.viewModel(
    scopeId: String,
    scopeName: String,
    viewModelQualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): Lazy<T> = lazy {
    createScope(scopeId, scopeName)
        .getViewModel(this, T::class, viewModelQualifier, parameters)
}

fun LifecycleOwner.createScope(
    scopeId: String,
    scopeName: String
) = getKoin().getOrCreateScope(scopeId, named(scopeName))

fun LifecycleOwner.getKoin() = (this as ComponentCallbacks).getKoin()
