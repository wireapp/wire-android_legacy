package com.waz.zclient.settings.account.editphonenumber

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.user.domain.usecase.phonenumber.Country
import kotlinx.coroutines.Dispatchers

class CountryCodePickerViewModel(private val getCountryCodesUseCase: GetCountryCodesUseCase) : ViewModel() {

    private var _countriesLiveData = MutableLiveData<List<Country>>()
    private var _countryLiveData = MutableLiveData<Country>()
    private var _dismissLiveData = MutableLiveData<Unit>()

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
