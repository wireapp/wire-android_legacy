package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.poovam.pinedittextfield.PinField.OnTextCompleteListener
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_create_personal_account_pin_code.*

class CreatePersonalAccountPinCodeFragment : Fragment(
    R.layout.fragment_create_personal_account_pin_code
) {

    //TODO handle no internet connections status
    private val createPersonalAccountViewModel: CreatePersonalAccountWithEmailViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeEmailValue()
        observeActivateEmailData()
        initPinCodeListener()
        initChangeMailListener()
    }

    private fun observeEmailValue() {
        createPersonalAccountViewModel.emailLiveData.observe(viewLifecycleOwner) {
            initDescriptionTextView(it)
            initResendCodeListener(it)
        }
    }

    private fun initDescriptionTextView(email: String) {
        createPersonalAccountPinCodeDescriptionTextView.text =
            getString(R.string.email_verification_description, email)
    }

    private fun initResendCodeListener(email: String) {
        createPersonalAccountPinCodeResendCodeTextView.setOnClickListener {
            createPersonalAccountViewModel.sendActivationCode(email)
        }
    }

    private fun initPinCodeListener() {
        createPersonalAccountPinCodePinEditText.onTextCompleteListener = object : OnTextCompleteListener {
            override fun onTextComplete(enteredText: String): Boolean {
                createPersonalAccountViewModel.activateEmail(enteredText)
                return true
            }
        }
    }

    private fun observeActivateEmailData() {
        with(createPersonalAccountViewModel) {
            activateEmailSuccessLiveData.observe(viewLifecycleOwner) {
                createPersonalAccountViewModel.saveActivationCode(createPersonalAccountPinCodePinEditText.text.toString())
                showEnterNameScreen()
            }
            activateEmailErrorLiveData.observe(viewLifecycleOwner) {
                showInvalidCodeError(getString(it.errorMessage))
                clearPinCode()
            }
        }
    }

    private fun showEnterNameScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountNameInputFragment.newInstance()
        )
    }

    private fun showInvalidCodeError(errorMessage: String) = AlertDialog.Builder(requireActivity())
        .setMessage(errorMessage)
        .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
        .create()
        .show()

    private fun clearPinCode() = createPersonalAccountPinCodePinEditText.text?.clear()

    private fun initChangeMailListener() {
        createPersonalAccountPinCodeChangeMailTextView.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    companion object {
        fun newInstance() = CreatePersonalAccountPinCodeFragment()
    }
}
