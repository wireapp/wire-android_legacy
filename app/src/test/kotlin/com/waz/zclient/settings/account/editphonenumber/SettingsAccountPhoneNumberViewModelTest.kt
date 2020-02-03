package com.waz.zclient.settings.account.editphonenumber

import com.waz.zclient.UnitTest
import com.waz.zclient.user.domain.usecase.phonenumber.ChangePhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.CountryCodeAndPhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.DeletePhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberUseCase
import org.junit.Before
import org.mockito.Mock

class SettingsAccountPhoneNumberViewModelTest : UnitTest() {

    private lateinit var settingsAccountPhoneNumberViewModel: SettingsAccountPhoneNumberViewModel

    @Mock
    private lateinit var changePhoneNumberUseCase: ChangePhoneNumberUseCase

    @Mock
    private lateinit var countryCodeAndPhoneNumberUseCase: CountryCodeAndPhoneNumberUseCase

    @Mock
    private lateinit var deletePhoneNumberUseCase: DeletePhoneNumberUseCase

    @Mock
    private lateinit var validatePhoneNumberUseCase: ValidatePhoneNumberUseCase

    @Before
    fun setup() {
        settingsAccountPhoneNumberViewModel = SettingsAccountPhoneNumberViewModel(
            validatePhoneNumberUseCase,
            changePhoneNumberUseCase,
            countryCodeAndPhoneNumberUseCase,
            deletePhoneNumberUseCase
        )
    }
}
