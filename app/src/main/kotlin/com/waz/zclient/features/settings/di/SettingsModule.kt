package com.waz.zclient.features.settings.di

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.waz.zclient.clients.domain.GetAllClientsUseCase
import com.waz.zclient.clients.domain.GetClientUseCase
import com.waz.zclient.features.settings.about.SettingsAboutViewModel
import com.waz.zclient.features.settings.account.SettingsAccountViewModel
import com.waz.zclient.features.settings.account.edithandle.SettingsAccountEditHandleViewModel
import com.waz.zclient.features.settings.account.editphonenumber.CountryCodePickerViewModel
import com.waz.zclient.features.settings.account.editphonenumber.GetCountryCodesUseCase
import com.waz.zclient.features.settings.account.editphonenumber.SettingsAccountPhoneNumberViewModel
import com.waz.zclient.features.settings.devices.detail.SettingsDeviceDetailViewModel
import com.waz.zclient.features.settings.devices.list.SettingsDeviceListViewModel
import com.waz.zclient.features.settings.support.SettingsSupportViewModel
import com.waz.zclient.settings.account.deleteaccount.DeleteAccountUseCase
import com.waz.zclient.settings.account.deleteaccount.SettingsAccountDeleteAccountViewModel
import com.waz.zclient.user.usecase.GetUserProfileUseCase
import com.waz.zclient.user.usecase.email.ChangeEmailUseCase
import com.waz.zclient.user.usecase.handle.ChangeHandleUseCase
import com.waz.zclient.user.usecase.handle.CheckHandleExistsUseCase
import com.waz.zclient.user.usecase.handle.GetHandleUseCase
import com.waz.zclient.user.usecase.handle.ValidateHandleUseCase
import com.waz.zclient.user.usecase.name.ChangeNameUseCase
import com.waz.zclient.user.usecase.phonenumber.ChangePhoneNumberUseCase
import com.waz.zclient.user.usecase.phonenumber.CountryCodeAndPhoneNumberUseCase
import com.waz.zclient.user.usecase.phonenumber.DeletePhoneNumberUseCase
import com.waz.zclient.user.usecase.phonenumber.ValidatePhoneNumberUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val SETTINGS_SCOPE_ID = "SettingsScopeId"
const val SETTINGS_SCOPE = "SettingsScope"

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
val settingsModules: List<Module>
    get() = listOf(
        settingsAboutModule,
        settingsAccountModule,
        settingsDeviceModule,
        settingsSupportModule
    )

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
val settingsAboutModule: Module = module {
    scope(named(SETTINGS_SCOPE)) {
        viewModel { SettingsAboutViewModel(get(), get(), get()) }
    }
}

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
val settingsSupportModule: Module = module {
    scope(named(SETTINGS_SCOPE)) {
        viewModel { SettingsSupportViewModel() }
    }
}

val settingsDeviceModule: Module = module {
    scope(named(SETTINGS_SCOPE)) {
        viewModel { SettingsDeviceListViewModel(get()) }
        viewModel { SettingsDeviceDetailViewModel(get()) }
        factory { GetAllClientsUseCase(get()) }
        factory { GetClientUseCase(get()) }
    }
}

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
val settingsAccountModule: Module = module {
    scope(named(SETTINGS_SCOPE)) {
        viewModel { SettingsAccountViewModel(get(), get(), get(), get(), get()) }
        viewModel { SettingsAccountEditHandleViewModel(get(), get(), get(), get()) }
        viewModel { SettingsAccountPhoneNumberViewModel(get(), get(), get(), get()) }
        viewModel { SettingsAccountDeleteAccountViewModel(get()) }
        viewModel { CountryCodePickerViewModel(get()) }

        scoped { PhoneNumberUtil.getInstance() }
        factory { ChangePhoneNumberUseCase(get()) }
        factory { DeletePhoneNumberUseCase(get()) }
        factory { CountryCodeAndPhoneNumberUseCase(get()) }
        factory { ValidatePhoneNumberUseCase() }
        factory { GetCountryCodesUseCase(get(), get()) }

        factory { CheckHandleExistsUseCase(get()) }
        factory { GetHandleUseCase(get()) }
        factory { ValidateHandleUseCase() }
        factory { ChangeHandleUseCase(get()) }

        factory { GetUserProfileUseCase(get()) }
        factory { ChangeNameUseCase(get()) }
        factory { ChangeEmailUseCase(get()) }
        factory { DeleteAccountUseCase(get()) }
    }
}
