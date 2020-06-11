package com.waz.zclient.feature.auth.registration.personal.phone.code

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
import com.waz.zclient.feature.auth.registration.personal.phone.CreatePersonalAccountPhoneCredentialsViewModel
import com.waz.zclient.feature.auth.registration.personal.phone.name.CreatePersonalAccountPhoneNameFragment
import kotlinx.android.synthetic.main.fragment_create_personal_account_phone_code.*

class CreatePersonalAccountPhoneCodeFragment : Fragment(
    R.layout.fragment_create_personal_account_phone_code
), DialogOwner {
    private val phoneCodeViewModel: CreatePersonalAccountPhoneCodeViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val phoneCredentialsViewModel: CreatePersonalAccountPhoneCredentialsViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    private val phone: String
        get() = phoneCredentialsViewModel.phone()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeActivatePhoneData()
        observeActivationCodeData()
        observeNetworkConnectionError()
        initDescriptionTextView()
        initResendCodeListener()
        initPinCodeListener()
        showKeyboard()
    }

    private fun initDescriptionTextView() {
        createPersonalAccountPhoneCodeDescriptionTextView.text =
            getString(R.string.create_personal_account_phone_code_description, phone)
    }

    private fun initResendCodeListener() {
        createPersonalAccountPhoneCodeResendCodeTextView.setOnClickListener {
            phoneCodeViewModel.sendActivationCode(phone)
        }
    }

    private fun initPinCodeListener() {
        createPersonalAccountPhoneCodePinEditText.onTextCompleteListener = object : OnTextCompleteListener {
            override fun onTextComplete(code: String): Boolean {
                phoneCodeViewModel.activatePhone(phone, code)
                return false
            }
        }
    }

    private fun observeActivatePhoneData() {
        with(phoneCodeViewModel) {
            activatePhoneSuccessLiveData.observe(viewLifecycleOwner) {
                phoneCredentialsViewModel.saveActivationCode(
                    createPersonalAccountPhoneCodePinEditText.text.toString()
                )
                showNameScreen()
            }
            activatePhoneErrorLiveData.observe(viewLifecycleOwner) {
                showGenericErrorDialog(it.message)
                clearPinCode()
                showKeyboard()
            }
        }
    }

    private fun observeActivationCodeData() {
        with(phoneCodeViewModel) {
            sendActivationCodeSuccessLiveData.observe(viewLifecycleOwner) {
                //TODO show correctly send activation code success messages
            }
            sendActivationCodeErrorLiveData.observe(viewLifecycleOwner) {
                showGenericErrorDialog(it.message)
            }
        }
    }

    private fun showNameScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountPhoneNameFragment.newInstance()
        )
    }

    private fun clearPinCode() = createPersonalAccountPhoneCodePinEditText.text?.clear()

    private fun observeNetworkConnectionError() {
        phoneCodeViewModel.networkConnectionErrorLiveData.observe(viewLifecycleOwner) {
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
        fun newInstance() = CreatePersonalAccountPhoneCodeFragment()
    }
}
