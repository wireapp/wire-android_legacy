package com.waz.zclient.settings.account.di

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.waz.zclient.settings.account.SettingsAccountViewModel
import com.waz.zclient.settings.account.edithandle.EditHandleViewModel
import com.waz.zclient.settings.account.editphonenumber.SettingsAccountPhoneNumberViewModel
import com.waz.zclient.settings.account.logout.LogoutDialogViewModel
import com.waz.zclient.settings.account.logout.LogoutUseCase
import com.waz.zclient.user.domain.usecase.ChangeEmailUseCase
import com.waz.zclient.user.domain.usecase.ChangeNameUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.CheckHandleExistsUseCase
import com.waz.zclient.user.domain.usecase.handle.GetHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.ChangePhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.CountryCodeAndPhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.DeletePhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
val settingsAccountModule: Module = module {
    viewModel { SettingsAccountViewModel(get(), get(), get()) }
    viewModel { EditHandleViewModel(get(), get(), get(), get()) }
    viewModel { SettingsAccountPhoneNumberViewModel(get(), get(), get(), get()) }

    single { PhoneNumberUtil.getInstance() }

    factory { DeletePhoneNumberUseCase(get()) }
    factory { CountryCodeAndPhoneNumberUseCase(get()) }
    factory { ValidatePhoneNumberUseCase() }
    viewModel { LogoutDialogViewModel(get()) }
    factory { LogoutUseCase(get(), get(), get()) }
    factory { CheckHandleExistsUseCase(get()) }
    factory { GetHandleUseCase(get()) }
    factory { ValidateHandleUseCase() }
    factory { GetUserProfileUseCase(get()) }
    factory { ChangeNameUseCase(get()) }
    factory { ChangePhoneNumberUseCase(get()) }
    factory { ChangeHandleUseCase(get()) }
    factory { ChangeEmailUseCase(get()) }
}
