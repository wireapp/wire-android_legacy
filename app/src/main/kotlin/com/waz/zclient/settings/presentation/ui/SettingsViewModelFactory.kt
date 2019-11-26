package com.waz.zclient.settings.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.waz.zclient.settings.presentation.ui.account.SettingsAccountViewModel
import com.waz.zclient.settings.user.usecase.GetUserProfileUseCase
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class SettingsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        with(modelClass) {
            return when {
                isAssignableFrom(SettingsAccountViewModel::class.java) -> { createSettingsViewModel() as T }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
    }

    private fun createSettingsViewModel() = SettingsAccountViewModel(GetUserProfileUseCase(Schedulers.io(), AndroidSchedulers.mainThread()))
}
