package com.waz.zclient.settings.account.editphonenumber

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.user.domain.usecase.phonenumber.Country

class CountryCodePickerViewModel(private val getCountryCodesUseCase: GetCountryCodesUseCase) : ViewModel() {

    private var _countriesLiveData = MutableLiveData<List<Country>>()
    private var _countryLiveData = MutableLiveData<Country>()

    val countriesLiveData: LiveData<List<Country>> = _countriesLiveData
    val countryLiveData: LiveData<Country> = _countryLiveData

    fun loadCountries(deviceLanguage: String) {
        getCountryCodesUseCase(viewModelScope, GetCountryCodesParams(deviceLanguage)) {
            it.fold({}, ::handleSuccess)
        }
    }

    private fun handleSuccess(countries: List<Country>) {
        _countriesLiveData.value = countries
    }

    fun onCountryCodeChanged(country: Country, countryDisplayName: String) {
        _countryLiveData.value =
            if (!countryDisplayName.equals(country.countryDisplayName, false)) {
                country
            } else Country.EMPTY
    }
}
