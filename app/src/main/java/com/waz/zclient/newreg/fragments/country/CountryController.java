/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.newreg.fragments.country;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CountryController {
    public static final String TAG = CountryController.class.getName();

    public Map<String, Country> codeToCountryMap = new HashMap<>();
    public Map<String, Country> abbreviationToCountryMap = new HashMap<>();

    private Country country;

    public CountryController(Context context) {
        populateCountries(context);
        String deviceCountry = getDeviceCountry(context);
        String defaultCountry = context.getResources().getString(R.string.new_reg__default_country);

        ArrayList<Country> countries = new ArrayList<>(abbreviationToCountryMap.values());

        for (Country country1 : countries) {
            if (country1.getAbbreviation().equalsIgnoreCase(deviceCountry)) {
                country = country1;
            }
        }

        if (country == null) {
            for (Country country1 : countries) {
                if (country1.getAbbreviation().equalsIgnoreCase(defaultCountry)) {
                    country = country1;
                }
            }
        }
    }

    public void setCountry(Country country) {
        this.country = country;

        for (Observer observer : observers) {
            observer.onCountryHasChanged(country);
        }
    }

    public Country getCountryFromCode(String code) {
        if (code == null) {
            return null;
        }
        return codeToCountryMap.get(code.replace("+", ""));
    }

    public String getCodeForAbbreviation(String abbreviation) {
        Country country = null;
        ArrayList<Country> countries = new ArrayList<>(abbreviationToCountryMap.values());
        for (Country country1 : countries) {
            if (country1.getAbbreviation().equalsIgnoreCase(abbreviation)) {
                country = country1;
                break;
            }
        }
        if (country == null) {
            return null;
        }
        return country.getCountryCode();
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void populateCountries(Context context) {
        List<Country> countries = new ArrayList<>();
        String deviceLanguage;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            //noinspection deprecation
            deviceLanguage = context.getResources().getConfiguration().locale.getLanguage();
        } else {
            deviceLanguage = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        }
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

        for (String region : phoneNumberUtil.getSupportedRegions()) {
            Locale locale = new Locale(deviceLanguage, region);
            Country country = new Country();
            country.setAbbreviation(locale.getCountry());
            country.setCountryCode(Integer.toString(phoneNumberUtil.getCountryCodeForRegion(region)));
            country.setName(locale.getDisplayCountry());
            countries.add(country);
        }

        for (Country country : countries) {
            codeToCountryMap.put(country.getCountryCode(), country);
            abbreviationToCountryMap.put(country.getAbbreviation(), country);
        }
    }

    public List<Country> getSortedCountries() {
        ArrayList<Country> countries = new ArrayList<>(abbreviationToCountryMap.values());
        Collections.sort(countries);
        if (BuildConfig.DEVELOPER_FEATURES_ENABLED) {
            Country qaShortcut = new Country();
            qaShortcut.setAbbreviation("QA-code");
            qaShortcut.setName("QA-Shortcut");
            qaShortcut.setCountryCode("0");
            countries.add(0, qaShortcut);
        }
        return countries;
    }

    public String getPhoneNumberWithoutCountryCode(String phoneNumber) {
        return getPhoneNumberWithoutCountryCode(phoneNumber, getSortedCountries());
    }

    public static String getPhoneNumberWithoutCountryCode(String phoneNumber, List<Country> countries) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return "";
        }

        final Set<String> countryCode = new HashSet<>();
        for (Country c : countries) {
            countryCode.add(c.getCountryCode());
        }

        if (phoneNumber.charAt(0) == '+') {
            if (phoneNumber.length() > 1) {
                phoneNumber = phoneNumber.substring(1);
            } else {
                return "";
            }
        }

        // All country codes are ISO3 so max length is 3 ==> <4
        if (phoneNumber.length() < 4) {
            return phoneNumber;
        }

        for (int i = 3; i >= 0; i--) {
            final String code = phoneNumber.substring(0, i + 1);
            if (countryCode.contains(code)) {
                return phoneNumber.substring(i + 1);
            }
        }

        return phoneNumber;
    }

    public static String getDeviceCountry(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String simCountry = telephonyManager.getSimCountryIso();

        if (simCountry != null) {
            return simCountry;
        } else {
            return telephonyManager.getNetworkCountryIso();
        }
    }

    private Set<Observer> observers = new HashSet<>();

    public void addObserver(Observer observer) {
        observers.add(observer);
        if (country != null) {
            observer.onCountryHasChanged(country);
        }
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    public interface Observer {
        void onCountryHasChanged(Country country);
    }
}
