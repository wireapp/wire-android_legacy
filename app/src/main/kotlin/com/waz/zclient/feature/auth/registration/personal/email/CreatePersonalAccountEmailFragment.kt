package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_create_personal_account_email.*

class CreatePersonalAccountEmailFragment : Fragment(R.layout.fragment_create_personal_account_email) {

    //TODO handle no internet connections status
    //TODO Add loading status
    private val createPersonalAccountViewModel: CreatePersonalAccountWithEmailViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeConfirmationData()
        observeActivationCodeData()
        initEmailChangedListener()
        initConfirmationButton()
    }

    private fun observeConfirmationData() {
        with(createPersonalAccountViewModel) {
            confirmationButtonEnabledLiveData.observe(viewLifecycleOwner) { updateConfirmationButtonStatus(it) }
        }
    }

    private fun updateConfirmationButtonStatus(enabled: Boolean) {
        createPersonalAccountEmailConfirmationButton.isEnabled = enabled
    }

    private fun initEmailChangedListener() {
        createPersonalAccountEmailEditText.doAfterTextChanged {
            createPersonalAccountViewModel.validateEmail(it.toString())
        }
    }

    private fun initConfirmationButton() {
        updateConfirmationButtonStatus(false)
        createPersonalAccountEmailConfirmationButton.setOnClickListener {
            with(createPersonalAccountViewModel) {
                sendActivationCode(createPersonalAccountEmailEditText.text.toString())
            }
        }
    }

    private fun observeActivationCodeData() {
        with(createPersonalAccountViewModel) {
            sendActivationCodeSuccessLiveData.observe(viewLifecycleOwner) {
                saveEmail(createPersonalAccountEmailEditText.text.toString())
                showEmailVerificationScreen()
                showEmailError(String.empty())
            }
            sendActivationCodeErrorLiveData.observe(viewLifecycleOwner) {
                showEmailError(getString(it.errorMessage))
            }
        }
    }

    private fun showEmailVerificationScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountPinCodeFragment.newInstance()
        )
    }

    private fun showEmailError(errorMessage: String) {
        createPersonalAccountEmailTextInputLayout.error = errorMessage
    }

    companion object {
        fun newInstance() = CreatePersonalAccountEmailFragment()
    }
}
