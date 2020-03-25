package com.waz.zclient.features.settings.di

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.waz.zclient.clients.usecase.GetAllClientsUseCase
import com.waz.zclient.clients.usecase.GetClientUseCase
import com.waz.zclient.features.settings.about.SettingsAboutViewModel
import com.waz.zclient.features.settings.account.SettingsAccountViewModel
import com.waz.zclient.features.settings.account.deleteaccount.DeleteAccountUseCase
import com.waz.zclient.features.settings.account.deleteaccount.SettingsAccountDeleteAccountViewModel
import com.waz.zclient.features.settings.account.edithandle.SettingsAccountEditHandleViewModel
import com.waz.zclient.features.settings.account.editphonenumber.CountryCodePickerViewModel
import com.waz.zclient.features.settings.account.editphonenumber.GetCountryCodesUseCase
import com.waz.zclient.features.settings.account.editphonenumber.SettingsAccountPhoneNumberViewModel
import com.waz.zclient.features.settings.devices.detail.SettingsDeviceDetailViewModel
import com.waz.zclient.features.settings.devices.list.SettingsDeviceListViewModel
import com.waz.zclient.features.settings.main.SettingsMainViewModel
import com.waz.zclient.features.settings.support.SettingsSupportViewModel
import com.waz.zclient.settings.about.SettingsAboutMainViewModel
import com.waz.zclient.settings.support.SettingsSupportMainViewModel
import com.waz.zclient.user.email.ChangeEmailUseCase
import com.waz.zclient.user.handle.usecase.ChangeHandleUseCase
import com.waz.zclient.user.handle.usecase.CheckHandleExistsUseCase
import com.waz.zclient.user.handle.usecase.GetHandleUseCase
import com.waz.zclient.user.handle.usecase.ValidateHandleUseCase
import com.waz.zclient.user.name.ChangeNameUseCase
import com.waz.zclient.user.phonenumber.usecase.ChangePhoneNumberUseCase
import com.waz.zclient.user.phonenumber.usecase.CountryCodeAndPhoneNumberUseCase
import com.waz.zclient.user.phonenumber.usecase.DeletePhoneNumberUseCase
import com.waz.zclient.user.phonenumber.usecase.ValidatePhoneNumberUseCase
import com.waz.zclient.user.profile.GetUserProfileUseCase
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
        settingsMainModule,
        settingsAboutModule,
        settingsAccountModule,
        settingsDeviceModule,
        settingsSupportModule
    )

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
val settingsMainModule: Module = module {
    scope(named(SETTINGS_SCOPE)) {
        viewModel { SettingsMainViewModel(get()) }
    }
}

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
val settingsAboutModule: Module = module {
    scope(named(SETTINGS_SCOPE)) {
        viewModel { SettingsAboutViewModel(get(), get(), get()) }
        viewModel { SettingsAboutMainViewModel(get()) }
    }
}

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
val settingsSupportModule: Module = module {
    scope(named(SETTINGS_SCOPE)) {
        viewModel { SettingsSupportViewModel() }
        viewModel { SettingsSupportMainViewModel(get()) }
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
