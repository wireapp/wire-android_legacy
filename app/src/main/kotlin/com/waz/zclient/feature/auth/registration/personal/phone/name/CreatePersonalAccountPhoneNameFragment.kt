package com.waz.zclient.feature.auth.registration.personal.phone.name

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
import com.waz.zclient.core.ui.dialog.DialogOwner
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import com.waz.zclient.feature.auth.registration.personal.phone.CreatePersonalAccountPhoneCredentialsViewModel
import kotlinx.android.synthetic.main.fragment_create_personal_account_name.*

class CreatePersonalAccountPhoneNameFragment : Fragment(
    R.layout.fragment_create_personal_account_name
), DialogOwner {

    private val nameViewModel: CreatePersonalAccountPhoneNameViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val credentialsViewModel: CreatePersonalAccountPhoneCredentialsViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    private val phone: String
        get() = credentialsViewModel.phone()

    private val activationCode: String
        get() = credentialsViewModel.activationCode()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeNameValidationData()
        observeRegistrationData()
        observeNetworkConnectionError()
        initNameChangedListener()
        initConfirmationButton()
        showKeyboard()
    }

    private fun observeNameValidationData() {
        nameViewModel.isValidNameLiveData.observe(viewLifecycleOwner) {
            updateConfirmationButtonStatus(it)
        }
    }

    private fun updateConfirmationButtonStatus(enabled: Boolean) {
        createPersonalAccountNameConfirmationButton.isEnabled = enabled
    }

    private fun initNameChangedListener() {
        createPersonalAccountNameEditText.doAfterTextChanged {
            nameViewModel.validateName(it.toString())
        }
    }

    private fun initConfirmationButton() {
        updateConfirmationButtonStatus(false)
        createPersonalAccountNameConfirmationButton.setOnClickListener {
            registerNewUser()
        }
    }

    private fun registerNewUser() {
        nameViewModel.register(
            name = createPersonalAccountNameEditText.text.toString(),
            phone = phone,
            activationCode = activationCode
        )
    }

    private fun observeRegistrationData() {
        with(nameViewModel) {
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
        nameViewModel.networkConnectionErrorLiveData.observe(viewLifecycleOwner) {
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
        fun newInstance() = CreatePersonalAccountPhoneNameFragment()
    }
}
