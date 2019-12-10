package com.waz.zclient.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.waz.zclient.settings.ui.account.SettingsAccountViewModel

@Suppress("UNCHECKED_CAST")
class SettingsViewModelFactory : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        with(modelClass) {
            return when {
                isAssignableFrom(SettingsAccountViewModel::class.java) -> {
                    createSettingsAccountViewModel() as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    private fun createSettingsAccountViewModel() = SettingsAccountViewModel()
}
