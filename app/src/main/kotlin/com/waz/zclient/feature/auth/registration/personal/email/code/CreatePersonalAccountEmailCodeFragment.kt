package com.waz.zclient.feature.auth.registration.personal.email.code

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
import com.waz.zclient.core.ui.dialog.DialogOwner
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountEmailCredentialsViewModel
import com.waz.zclient.feature.auth.registration.personal.email.name.CreatePersonalAccountEmailNameFragment
import kotlinx.android.synthetic.main.fragment_create_personal_account_email_code.*

class CreatePersonalAccountEmailCodeFragment : Fragment(
    R.layout.fragment_create_personal_account_email_code
), DialogOwner {

    private val emailCodeViewModel: CreatePersonalAccountEmailCodeViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val emailCredentialsViewModel: CreatePersonalAccountEmailCredentialsViewModel
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
        createPersonalAccountEmailCodeDescriptionTextView.text =
            getString(R.string.create_personal_account_email_code_description, email)
    }

    private fun initResendCodeListener() {
        createPersonalAccountEmailCodeResendCodeTextView.setOnClickListener {
            emailCodeViewModel.sendActivationCode(email)
        }
    }

    private fun initPinCodeListener() {
        createPersonalAccountEmailCodePinEditText.onTextCompleteListener = object : OnTextCompleteListener {
            override fun onTextComplete(code: String): Boolean {
                emailCodeViewModel.activateEmail(email, code)
                return false
            }
        }
    }

    private fun observeActivateEmailData() {
        with(emailCodeViewModel) {
            activateEmailSuccessLiveData.observe(viewLifecycleOwner) {
                emailCredentialsViewModel.saveActivationCode(
                    createPersonalAccountEmailCodePinEditText.text.toString()
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
        with(emailCodeViewModel) {
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
            CreatePersonalAccountEmailNameFragment.newInstance()
        )
    }

    private fun clearPinCode() = createPersonalAccountEmailCodePinEditText.text?.clear()

    private fun initChangeMailListener() {
        createPersonalAccountEmailCodeChangeMailTextView.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun observeNetworkConnectionError() {
        emailCodeViewModel.networkConnectionErrorLiveData.observe(viewLifecycleOwner) {
            showNetworkConnectionErrorDialog()
        }
    }

    private fun showNetworkConnectionErrorDialog() = AlertDialog.Builder(context)
        .setTitle(R.string.no_internet_connection_title)
        .setMessage(R.string.no_internet_connection_message)
        .setPositiveButton(android.R.string.ok) { _, _ -> }
        .create()
        .show()

    private fun showGenericErrorDialog(messageResId: Int) = showErrorDialog(
        requireContext(),
        getString(messageResId)
    )

    companion object {
        fun newInstance() = CreatePersonalAccountEmailCodeFragment()
    }
}
