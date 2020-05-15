package com.waz.zclient.core.extension

import android.content.ComponentCallbacks
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import org.koin.android.ext.android.getKoin
import org.koin.android.viewmodel.ViewModelParameters
import org.koin.android.viewmodel.ViewModelStoreOwnerDefinition
import org.koin.android.viewmodel.ext.android.getViewModel
import org.koin.android.viewmodel.getViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import kotlin.reflect.KClass

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
        .getViewModel(this@viewModel, T::class, viewModelQualifier, parameters)
}

/**
 * Lazy getByClass a viewModel instance shared with Activity
 *
 * @param scopeId - Is used to find the scope in reference for the ViewModel you're trying to retrieve
 * @param qualifier - Koin BeanDefinition qualifier (if have several ViewModel beanDefinition of the same type)
 * @param from - ViewModelStoreOwner that will store the viewModel instance. Examples:
 *               "parentFragment", "activity". Default: "activity"
 * @param parameters - parameters to pass to the BeanDefinition
 */
inline fun <reified T : ViewModel> Fragment.sharedViewModel(
    scopeId: String,
    qualifier: Qualifier? = null,
    noinline from: ViewModelStoreOwnerDefinition = { activity as ViewModelStoreOwner },
    noinline parameters: ParametersDefinition? = null
): Lazy<T> = lazy {
    getKoin().getScope(scopeId)
        .getSharedViewModel(this@sharedViewModel, T::class, qualifier, from, parameters)
}

/**
 * Get a shared viewModel instance from underlying Activity
 *
 * @param lifecycleOwner - The LifecycleOwner looking for the sharedViewModel
 * @param qualifier - Koin BeanDefinition qualifier (if have several ViewModel beanDefinition of the same type)
 * @param from - ViewModelStoreOwner that will store the viewModel instance.
 *               Examples: ("parentFragment", "activity"). Default: "activity"
 * @param parameters - parameters to pass to the BeanDefinition
 * @param clazz - KClass instance of the ViewModel needed
 */
fun <T : ViewModel> Scope.getSharedViewModel(
    lifecycleOwner: LifecycleOwner,
    clazz: KClass<T>,
    qualifier: Qualifier?,
    from: () -> ViewModelStoreOwner,
    parameters: ParametersDefinition?
): T = getViewModel(
    ViewModelParameters(
        clazz,
        lifecycleOwner,
        qualifier,
        from,
        parameters
    )
)
