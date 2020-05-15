package com.waz.zclient.feature.auth.registration.personal.password

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.showKeyboard
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import com.waz.zclient.feature.auth.registration.personal.email.EmailCredentialsViewModel
import kotlinx.android.synthetic.main.fragment_create_personal_account_password.*

class CreatePersonalAccountPasswordFragment : Fragment(R.layout.fragment_create_personal_account_password) {

    private val createPersonalAccountPasswordViewModel: CreatePersonalAccountPasswordViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val emailCredentialsViewModel: EmailCredentialsViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    private val name: String
        get() = emailCredentialsViewModel.name()

    private val email: String
        get() = emailCredentialsViewModel.email()

    private val activationCode: String
        get() = emailCredentialsViewModel.activationCode()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observePasswordValidationData()
        observeRegistrationData()
        observeNetworkConnectionError()
        initPasswordChangedListener()
        initConfirmationButton()
        showKeyboard()
    }

    private fun observePasswordValidationData() {
        createPersonalAccountPasswordViewModel.isValidPasswordLiveData.observe(viewLifecycleOwner) {
            updateConfirmationButtonStatus(it)
        }
    }

    private fun updateConfirmationButtonStatus(status: Boolean) {
        createPersonalAccountPasswordConfirmationButton.isEnabled = status
    }

    private fun initPasswordChangedListener() {
        createPersonalAccountPasswordEditText.doAfterTextChanged {
            createPersonalAccountPasswordViewModel.validatePassword(
                it.toString()
            )
        }
    }

    private fun initConfirmationButton() {
        updateConfirmationButtonStatus(false)
        createPersonalAccountPasswordConfirmationButton.setOnClickListener {
            registerNewUser()
        }
    }

    private fun registerNewUser() {
        createPersonalAccountPasswordViewModel.register(
            name = name,
            email = email,
            activationCode = activationCode,
            password = createPersonalAccountPasswordEditText.text.toString()
        )
    }

    private fun observeRegistrationData() {
        with(createPersonalAccountPasswordViewModel) {
            registerSuccessLiveData.observe(viewLifecycleOwner) {
                //TODO move the new registered user to right scala activity/fragment
                Toast.makeText(requireContext(), getString(R.string.alert_dialog__confirmation),
                    Toast.LENGTH_LONG).show()
            }
            registerErrorLiveData.observe(viewLifecycleOwner) {
                showGenericErrorDialog(it.message)
            }
        }
    }

    private fun observeNetworkConnectionError() {
        createPersonalAccountPasswordViewModel.networkConnectionErrorLiveData.observe(viewLifecycleOwner) {
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
        fun newInstance() = CreatePersonalAccountPasswordFragment()
    }
}
