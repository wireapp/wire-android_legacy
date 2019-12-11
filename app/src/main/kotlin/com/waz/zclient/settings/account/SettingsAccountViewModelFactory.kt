package com.waz.zclient.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.domain.usecase.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.ChangePhoneUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase

@Suppress("UNCHECKED_CAST")
class SettingsAccountViewModelFactory : ViewModelProvider.Factory {

    private val getUserProfileUseCase by lazy {
        GetUserProfileUseCase(UsersRepository.getInstance())
    }

    private val changeHandleUseCase by lazy {
        ChangeHandleUseCase(UsersRepository.getInstance())
    }

    private val changePhoneUseCase by lazy {
        ChangePhoneUseCase(UsersRepository.getInstance())
    }

    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        with(modelClass) {
            return when {
                isAssignableFrom(SettingsAccountViewModel::class.java) -> {
                    createSettingsAccountViewModel() as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    private fun createSettingsAccountViewModel() = SettingsAccountViewModel(getUserProfileUseCase, changeHandleUseCase, changePhoneUseCase)
}
