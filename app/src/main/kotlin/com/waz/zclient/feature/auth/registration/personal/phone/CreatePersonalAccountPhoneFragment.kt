package com.waz.zclient.feature.auth.registration.personal.phone

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import com.waz.zclient.feature.auth.registration.personal.phone.code.CreatePersonalAccountPhoneCodeFragment
import com.waz.zclient.shared.countrycode.Country
import com.waz.zclient.shared.countrycode.CountryCodePickerFragment
import kotlinx.android.synthetic.main.fragment_create_personal_account_phone.*

class CreatePersonalAccountPhoneFragment : Fragment(R.layout.fragment_create_personal_account_phone) {

    //TODO Add loading status
    private val phoneViewModel: CreatePersonalAccountPhoneViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val phoneCredentialsViewModel: CreatePersonalAccountPhoneCredentialsViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observePhoneValidationData()
        observeActivationCodeData()
        observeNetworkConnectionError()
        initCountryCodePickerListener()
        initPhoneChangedListener()
        initConfirmationButton()
    }

    private fun observePhoneValidationData() {
        phoneViewModel.isValidPhoneLiveData.observe(viewLifecycleOwner) {
            updateConfirmationButtonStatus(it)
        }
    }

    private fun updateConfirmationButtonStatus(enabled: Boolean) {
        createPersonalAccountPhoneConfirmationButton.isEnabled = enabled
    }

    private fun initCountryCodePickerListener() {
        createPersonalAccountCountryCodeCountryCodePickerTextView.setOnClickListener {
            showCountryCodePickerDialog(createPersonalAccountCountryCodeEditText.text.toString())
        }
    }

    private fun initPhoneChangedListener() {
        createPersonalAccountCountryCodeEditText.doAfterTextChanged {
            phoneViewModel.validatePhone(it.toString(), createPersonalAccountPhoneEditText.text.toString())
        }
        createPersonalAccountPhoneEditText.doAfterTextChanged {
            phoneViewModel.validatePhone(createPersonalAccountCountryCodeEditText.text.toString(), it.toString())
        }
    }

    private fun initConfirmationButton() {
        updateConfirmationButtonStatus(false)
        createPersonalAccountPhoneConfirmationButton.setOnClickListener {
            phoneViewModel.sendActivationCode(
                createPersonalAccountPhoneEditText.text.toString()
            )
        }
    }

    private fun observeActivationCodeData() {
        with(phoneViewModel) {
            sendActivationCodeSuccessLiveData.observe(viewLifecycleOwner) {
                phoneCredentialsViewModel.savePhone(
                    createPersonalAccountPhoneEditText.text.toString()
                )
                showPhoneCodeScreen()
            }
            sendActivationCodeErrorLiveData.observe(viewLifecycleOwner) {
                showGenericErrorDialog(it.message)
            }
        }
    }

    private fun showPhoneCodeScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountPhoneCodeFragment.newInstance()
        )
    }

    private fun observeNetworkConnectionError() {
        phoneViewModel.networkConnectionErrorLiveData.observe(viewLifecycleOwner) {
            showNetworkConnectionErrorDialog()
        }
    }

    private fun showNetworkConnectionErrorDialog() = AlertDialog.Builder(context)
        .setTitle(R.string.no_internet_connection_title)
        .setMessage(R.string.no_internet_connection_message)
        .setPositiveButton(android.R.string.ok) { _, _ -> }
        .create()
        .show()

    private fun showGenericErrorDialog(messageResId: Int) = AlertDialog.Builder(context)
        .setMessage(messageResId)
        .setPositiveButton(android.R.string.ok) { _, _ -> }
        .create()
        .show()

    private fun showCountryCodePickerDialog(countryDisplayName: String) {
        CountryCodePickerFragment.newInstance(
            countryDisplayName,
            object : CountryCodePickerFragment.CountryCodePickerListener {
                override fun onCountryCodeSelected(countryCode: Country) {
                    createPersonalAccountCountryCodeEditText.setText(countryCode.countryCode)
                    createPersonalAccountCountryCodeCountryCodePickerTextView.text = countryCode.countryDisplayName
                }
            }
        ).show(requireActivity().supportFragmentManager, String.empty())
    }

    companion object {
        fun newInstance() = CreatePersonalAccountPhoneFragment()
    }
}
