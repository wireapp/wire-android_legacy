package com.waz.zclient.settings.account.editphonenumber

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.removeFragment
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.withArgs
import kotlinx.android.synthetic.main.fragment_edit_phone.*
import kotlinx.android.synthetic.main.fragment_edit_phone.view.*
import org.koin.android.viewmodel.ext.android.viewModel

class EditPhoneNumberFragment : Fragment() {

    private lateinit var rootView: View

    private val phoneNumber: String by lazy {
        arguments?.getString(CURRENT_PHONE_NUMBER_KEY, String.empty()) ?: String.empty()
    }

    private val hasEmail: Boolean by lazy {
        arguments?.getBoolean(HAS_EMAIL_BUNDLE_KEY, false) ?: false
    }

    private val settingsAccountPhoneNumberViewModel: SettingsAccountPhoneNumberViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_edit_phone, container, false)

        initToolbar()
        initPhoneInput()
        initCountryCodeInput()
        initDeleteNumberButton()
        initSaveButton()

        lifecycleScope.launchWhenResumed {
            settingsAccountPhoneNumberViewModel.loadPhoneNumberData(phoneNumber)
        }
        return rootView
    }

    private fun initSaveButton() {
        rootView.editPhoneSavePhoneNumberTextView.setOnClickListener {
            confirmPhoneNumber()
        }
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref__account_action__dialog__edit_phone__title)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initDeleteNumberButton() {
        rootView.editPhoneDeletePhoneNumberTextView.visibility = (if (hasEmail) View.VISIBLE else View.INVISIBLE)
        rootView.editPhoneDeletePhoneNumberTextView.setOnClickListener {
            settingsAccountPhoneNumberViewModel.onDeleteNumberButtonClicked(
                rootView.editPhoneCountryCodeTextInputEditText.text.toString(),
                rootView.editPhonePhoneNumberTextInputEditText.text.toString()
            )
        }

        settingsAccountPhoneNumberViewModel.deleteNumberLiveData.observe(viewLifecycleOwner) {
            showDeleteNumberDialog(it)
        }
    }

    private fun initCountryCodeInput() {
        settingsAccountPhoneNumberViewModel.countryCodeLiveData.observe(viewLifecycleOwner) {
            rootView.editPhoneCountryCodeTextInputEditText.setText(it)
        }
        settingsAccountPhoneNumberViewModel.countryCodeErrorLiveData.observe(viewLifecycleOwner) {
            updateCountryCodeError(getString(it.errorMessage))
        }
    }

    private fun initPhoneInput() {
        rootView.editPhonePhoneNumberTextInputEditText.requestFocus()
        rootView.editPhonePhoneNumberTextInputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmPhoneNumber()
                true
            } else false
        }

        with(settingsAccountPhoneNumberViewModel) {
            phoneNumberLiveData.observe(viewLifecycleOwner) {
                rootView.editPhonePhoneNumberTextInputEditText.setText(it)
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
        replaceFragment(
            R.id.editPhoneActivityFragmentContainer,
            VerifyPhoneFragment.newInstance(phoneNumber),
            true)
        (activity as AppCompatActivity).removeFragment(this)
    }

    private fun confirmPhoneNumber() {
        settingsAccountPhoneNumberViewModel.afterNumberEntered(
            rootView.editPhoneCountryCodeTextInputEditText.text.toString(),
            rootView.editPhonePhoneNumberTextInputEditText.text.toString()
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
