package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.core.ui.dialog.Alert
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_create_personal_account_email.*

class CreatePersonalAccountEmailFragment : Fragment(R.layout.fragment_create_personal_account_email) {

    //TODO Add loading status
    private val createPersonalAccountWithEmailViewModel: CreatePersonalAccountWithEmailViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val emailCredentialsViewModel: EmailCredentialsViewModel by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeEmailValidationData()
        observeActivationCodeData()
        observeNetworkConnectionError()
        initEmailChangedListener()
        initConfirmationButton()
    }

    private fun observeEmailValidationData() {
        createPersonalAccountWithEmailViewModel.isValidEmailLiveData.observe(viewLifecycleOwner) {
            updateConfirmationButtonStatus(it)
        }
    }

    private fun updateConfirmationButtonStatus(enabled: Boolean) {
        createPersonalAccountEmailConfirmationButton.isEnabled = enabled
    }

    private fun initEmailChangedListener() {
        createPersonalAccountEmailEditText.doAfterTextChanged {
            createPersonalAccountWithEmailViewModel.validateEmail(it.toString())
        }
    }

    private fun initConfirmationButton() {
        updateConfirmationButtonStatus(false)
        createPersonalAccountEmailConfirmationButton.setOnClickListener {
            createPersonalAccountWithEmailViewModel.sendActivationCode(
                createPersonalAccountEmailEditText.text.toString()
            )
        }
    }

    private fun observeActivationCodeData() {
        with(createPersonalAccountWithEmailViewModel) {
            sendActivationCodeSuccessLiveData.observe(viewLifecycleOwner) {
                emailCredentialsViewModel.saveEmail(
                    createPersonalAccountEmailEditText.text.toString()
                )
                showEmailVerificationScreen()
            }
            sendActivationCodeErrorLiveData.observe(viewLifecycleOwner) {
                Alert.showError(requireContext(), getString(it.message))
            }
        }
    }

    private fun showEmailVerificationScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountPinCodeFragment.newInstance()
        )
    }

    private fun observeNetworkConnectionError() {
        createPersonalAccountWithEmailViewModel.networkConnectionErrorLiveData.observe(viewLifecycleOwner) {
            Alert.showNetworkConnectionError(requireContext())
        }
    }

    companion object {
        fun newInstance() = CreatePersonalAccountEmailFragment()
    }
}
