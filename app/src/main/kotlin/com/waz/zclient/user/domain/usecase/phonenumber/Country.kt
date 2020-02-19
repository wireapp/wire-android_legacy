package com.waz.zclient.user.domain.usecase.phonenumber

import com.waz.zclient.core.extension.empty

data class Country(val country: String, val countryDisplayName: String, val countryCode: String) {
    companion object {
        val EMPTY = Country(String.empty(), String.empty(), String.empty())
    }
}
