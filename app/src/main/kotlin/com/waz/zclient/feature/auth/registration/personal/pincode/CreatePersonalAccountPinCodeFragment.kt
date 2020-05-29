package com.waz.zclient.feature.auth.registration.personal.pincode

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.poovam.pinedittextfield.PinField.OnTextCompleteListener
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.showKeyboard
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import com.waz.zclient.feature.auth.registration.personal.email.EmailCredentialsViewModel
import com.waz.zclient.feature.auth.registration.personal.name.CreatePersonalAccountNameFragment
import kotlinx.android.synthetic.main.fragment_create_personal_account_pin_code.*

class CreatePersonalAccountPinCodeFragment : Fragment(
    R.layout.fragment_create_personal_account_pin_code
) {

    private val createPersonalAccountPinCodeViewModel: CreatePersonalAccountPinCodeViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val emailCredentialsViewModel: EmailCredentialsViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    private val email: String
        get() = emailCredentialsViewModel.email()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeActivateEmailData()
        observeActivationCodeData()
        observeNetworkConnectionError()
        initChangeMailListener()
        initDescriptionTextView()
        initResendCodeListener()
        initPinCodeListener()
        showKeyboard()
    }

    private fun initDescriptionTextView() {
        createPersonalAccountPinCodeDescriptionTextView.text =
            getString(R.string.email_verification_description, email)
    }

    private fun initResendCodeListener() {
        createPersonalAccountPinCodeResendCodeTextView.setOnClickListener {
            createPersonalAccountPinCodeViewModel.sendActivationCode(email)
        }
    }

    private fun initPinCodeListener() {
        createPersonalAccountPinCodePinEditText.onTextCompleteListener = object : OnTextCompleteListener {
            override fun onTextComplete(code: String): Boolean {
                createPersonalAccountPinCodeViewModel.activateEmail(email, code)
                return false
            }
        }
    }

    private fun observeActivateEmailData() {
        with(createPersonalAccountPinCodeViewModel) {
            activateEmailSuccessLiveData.observe(viewLifecycleOwner) {
                emailCredentialsViewModel.saveActivationCode(
                    createPersonalAccountPinCodePinEditText.text.toString()
                )
                showEnterNameScreen()
            }
            activateEmailErrorLiveData.observe(viewLifecycleOwner) {
                showGenericErrorDialog(it.message)
                clearPinCode()
                showKeyboard()
            }
        }
    }

    private fun observeActivationCodeData() {
        with(createPersonalAccountPinCodeViewModel) {
            sendActivationCodeSuccessLiveData.observe(viewLifecycleOwner) {
                //TODO show correctly send activation code success messages
            }
            sendActivationCodeErrorLiveData.observe(viewLifecycleOwner) {
                showGenericErrorDialog(it.message)
            }
        }
    }

    private fun showEnterNameScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountNameFragment.newInstance()
        )
    }

    private fun clearPinCode() = createPersonalAccountPinCodePinEditText.text?.clear()

    private fun initChangeMailListener() {
        createPersonalAccountPinCodeChangeMailTextView.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun observeNetworkConnectionError() {
        createPersonalAccountPinCodeViewModel.networkConnectionErrorLiveData.observe(viewLifecycleOwner) {
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

    companion object {
        fun newInstance() = CreatePersonalAccountPinCodeFragment()
    }
}
