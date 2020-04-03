package com.waz.zclient.feature.settings.account.editphonenumber

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.getDeviceLocale
import com.waz.zclient.core.extension.removeFragment
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.feature.settings.di.SETTINGS_SCOPE_ID
import com.waz.zclient.shared.user.phonenumber.Country
import kotlinx.android.synthetic.main.fragment_edit_phone.*

@SuppressWarnings("TooManyFunctions")
class EditPhoneNumberFragment : Fragment(R.layout.fragment_edit_phone) {

    private val phoneViewModel by sharedViewModel<SettingsAccountPhoneNumberViewModel>(SETTINGS_SCOPE_ID)

    private val phoneNumber: String by lazy {
        arguments?.getString(CURRENT_PHONE_NUMBER_KEY, String.empty()) ?: String.empty()
    }

    private val hasEmail: Boolean by lazy {
        arguments?.getBoolean(HAS_EMAIL_BUNDLE_KEY, false) ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initCountryCodePicker()
        initPhoneInput()
        initCountryCodeInput()
        initDeleteNumberButton()
        initSaveButton()

        lifecycleScope.launchWhenResumed {
            phoneViewModel.loadPhoneNumberData(
                phoneNumber,
                requireActivity().getDeviceLocale().language
            )
        }
    }

    private fun initCountryCodePicker() {
        phoneViewModel.phoneNumberDetailsLiveData.observe(viewLifecycleOwner) {
            editPhoneFragmentCountryCodePicker.text = it.country
        }

        editPhoneFragmentCountryCodePicker.setOnClickListener {
            showCountryCodePickerDialog(editPhoneFragmentCountryCodePicker.text.toString())
        }
    }

    private fun initSaveButton() {
        editPhoneSavePhoneNumberTextView.setOnClickListener {
            confirmPhoneNumber()
        }
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref__account_action__dialog__edit_phone__title)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initDeleteNumberButton() {
        editPhoneDeletePhoneNumberTextView.visibility = (if (hasEmail) View.VISIBLE else View.INVISIBLE)
        editPhoneDeletePhoneNumberTextView.setOnClickListener {
            phoneViewModel.onDeleteNumberButtonClicked(
                editPhoneCountryCodeTextInputEditText.text.toString(),
                editPhonePhoneNumberTextInputEditText.text.toString()
            )
        }

        phoneViewModel.deleteNumberLiveData.observe(viewLifecycleOwner) {
            showDeleteNumberDialog(it)
        }
    }

    private fun initCountryCodeInput() {
        phoneViewModel.phoneNumberDetailsLiveData.observe(viewLifecycleOwner) {
            editPhoneCountryCodeTextInputEditText.setText(it.countryCode)
        }
        phoneViewModel.countryCodeErrorLiveData.observe(viewLifecycleOwner) {
            updateCountryCodeError(getString(it.errorMessage))
        }
    }

    private fun initPhoneInput() {
        editPhonePhoneNumberTextInputEditText.requestFocus()
        editPhonePhoneNumberTextInputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmPhoneNumber()
                true
            } else false
        }

        with(phoneViewModel) {
            phoneNumberDetailsLiveData.observe(viewLifecycleOwner) {
                editPhonePhoneNumberTextInputEditText.setText(it.number)
            }
            phoneNumberErrorLiveData.observe(viewLifecycleOwner) {
                updatePhoneNumberError(getString(it.errorMessage))
            }
            confirmationLiveData.observe(viewLifecycleOwner) {
                showConfirmationDialog(it)
            }
            confirmedLiveData.observe(viewLifecycleOwner) {
                showVerificationScreen(it)
            }
        }
    }

    private fun showVerificationScreen(phoneNumber: String) {
        (activity as AppCompatActivity).replaceFragment(
            R.id.editPhoneActivityFragmentContainer,
            VerifyPhoneFragment.newInstance(phoneNumber))
        (activity as AppCompatActivity).removeFragment(this)
    }

    private fun confirmPhoneNumber() {
        phoneViewModel.afterNumberEntered(
            editPhoneCountryCodeTextInputEditText.text.toString(),
            editPhonePhoneNumberTextInputEditText.text.toString()
        )
    }

    private fun updateCountryCodeError(errorMessage: String) {
        editPhonePhoneNumberTextInputLayout.error = errorMessage
    }

    private fun updatePhoneNumberError(errorMessage: String) {
        editPhonePhoneNumberTextInputLayout.error = errorMessage
    }

    private fun showDeleteNumberDialog(phoneNumber: String) {
        DeletePhoneDialogFragment.newInstance(phoneNumber)
            .show(requireActivity().supportFragmentManager, String.empty())
    }

    private fun showConfirmationDialog(phoneNumber: String) {
        UpdatePhoneDialogFragment.newInstance(phoneNumber)
            .show(requireActivity().supportFragmentManager, String.empty())
    }

    private fun showCountryCodePickerDialog(countryDisplayName: String) {
        CountryCodePickerFragment.newInstance(
            countryDisplayName,
            object : CountryCodePickerFragment.CountryCodePickerListener {
                override fun onCountryCodeSelected(countryCode: Country) {
                    phoneViewModel.onCountryCodeUpdated(countryCode)
                }
            }
        ).show(requireActivity().supportFragmentManager, String.empty())
    }

    companion object {
        private const val CURRENT_PHONE_NUMBER_KEY = "currentPhoneNumber"
        private const val HAS_EMAIL_BUNDLE_KEY = "hasEmailBundleKey"

        fun newInstance(phoneNumber: String, hasEmail: Boolean) =
            EditPhoneNumberFragment().withArgs {
                putString(CURRENT_PHONE_NUMBER_KEY, phoneNumber)
                putBoolean(HAS_EMAIL_BUNDLE_KEY, hasEmail)
            }
    }
}
