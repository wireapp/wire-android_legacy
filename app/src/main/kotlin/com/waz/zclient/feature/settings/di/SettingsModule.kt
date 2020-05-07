package com.waz.zclient.feature.settings.di

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.waz.zclient.feature.settings.about.SettingsAboutViewModel
import com.waz.zclient.feature.settings.account.SettingsAccountViewModel
import com.waz.zclient.feature.settings.account.deleteaccount.DeleteAccountUseCase
import com.waz.zclient.feature.settings.account.deleteaccount.SettingsAccountDeleteAccountViewModel
import com.waz.zclient.feature.settings.account.edithandle.SettingsAccountEditHandleViewModel
import com.waz.zclient.feature.settings.account.editphonenumber.CountryCodePickerViewModel
import com.waz.zclient.feature.settings.account.editphonenumber.GetCountryCodesUseCase
import com.waz.zclient.feature.settings.account.editphonenumber.SettingsAccountPhoneNumberViewModel
import com.waz.zclient.feature.settings.account.logout.LogoutUseCase
import com.waz.zclient.feature.settings.account.logout.LogoutViewModel
import com.waz.zclient.feature.settings.devices.detail.SettingsDeviceDetailViewModel
import com.waz.zclient.feature.settings.devices.list.SettingsDeviceListViewModel
import com.waz.zclient.feature.settings.support.SettingsSupportViewModel
import com.waz.zclient.shared.clients.usecase.GetAllClientsUseCase
import com.waz.zclient.shared.clients.usecase.GetClientUseCase
import com.waz.zclient.shared.user.email.ChangeEmailUseCase
import com.waz.zclient.shared.user.handle.usecase.ChangeHandleUseCase
import com.waz.zclient.shared.user.handle.usecase.CheckHandleExistsUseCase
import com.waz.zclient.shared.user.handle.usecase.GetHandleUseCase
import com.waz.zclient.shared.user.handle.usecase.ValidateHandleUseCase
import com.waz.zclient.shared.user.name.ChangeNameUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.ChangePhoneNumberUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.CountryCodeAndPhoneNumberUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.DeletePhoneNumberUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.ValidatePhoneNumberUseCase
import com.waz.zclient.shared.user.profile.GetUserProfileUseCase
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
        viewModel { LogoutViewModel(get()) }

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

        factory { LogoutUseCase(get(), get(), get()) }
    }
}
