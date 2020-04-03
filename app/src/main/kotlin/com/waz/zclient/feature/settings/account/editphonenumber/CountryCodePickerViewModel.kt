package com.waz.zclient.feature.settings.account.editphonenumber

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.shared.user.phonenumber.Country
import kotlinx.coroutines.Dispatchers

class CountryCodePickerViewModel(private val getCountryCodesUseCase: GetCountryCodesUseCase) : ViewModel() {

    private val _countriesLiveData = MutableLiveData<List<Country>>()
    private val _countryLiveData = MutableLiveData<Country>()
    private val _dismissLiveData = MutableLiveData<Unit>()

    val countriesLiveData: LiveData<List<Country>> = _countriesLiveData
    val dismissLiveData: LiveData<Unit> = _dismissLiveData
    val selectedCountryLiveData: LiveData<Country> = _countryLiveData

    fun loadCountries(deviceLanguage: String) {
        getCountryCodesUseCase(viewModelScope, GetCountryCodesParams(deviceLanguage), Dispatchers.Default) {
            it.fold({}, ::handleSuccess)
        }
    }

    private fun handleSuccess(countries: List<Country>) {
        _countriesLiveData.value = countries
    }

    fun onCountryCodeChanged(country: Country, countryDisplayName: String) {
        if (!countryDisplayName.equals(country.countryDisplayName, false)) {
            _countryLiveData.value = country
            dismissDialog()
        } else dismissDialog()
    }

    private fun dismissDialog() {
        _dismissLiveData.value = Unit
    }
}
