package com.waz.zclient.shared.countrycode.di

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.waz.zclient.shared.countrycode.CountryCodePickerViewModel
import com.waz.zclient.shared.countrycode.usecase.GetCountryCodesUseCase
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val countryCodePickerModule: Module = module {
    viewModel { CountryCodePickerViewModel(get()) }
    factory { GetCountryCodesUseCase(get(), get()) }
    single { PhoneNumberUtil.getInstance() }
}