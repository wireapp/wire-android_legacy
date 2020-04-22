package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_create_personal_account_password.*


class CreatePersonalAccountPasswordFragment : Fragment(R.layout.fragment_create_personal_account_password) {

    private val createPersonalAccountWithEmailViewModel: CreatePersonalAccountWithEmailViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val createPersonalAccountWithEmailSharedViewModel: CreatePersonalAccountWithEmailSharedViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeCredentials()
        observeRegistrationData()
    }

    private fun observeCredentials() {
        createPersonalAccountWithEmailSharedViewModel.credentialsLiveData.observe(viewLifecycleOwner) {
            initConfirmationButton(it)
        }
    }

    private fun initConfirmationButton(credentials: Credentials) {
        createPersonalAccountPasswordConfirmationButton.setOnClickListener {
            registerNewUser(credentials)
        }
    }

    private fun registerNewUser(credentials: Credentials) {
        createPersonalAccountWithEmailViewModel.register(
            name = credentials.name,
            email = credentials.email,
            activationCode = credentials.activationCode,
            password = createPersonalAccountPasswordEditText.text.toString()
        )
    }

    private fun observeRegistrationData() {
        with(createPersonalAccountWithEmailViewModel) {
            registerSuccessLiveData.observe(viewLifecycleOwner)
            {
                //TODO move the new registered user to right scala activity/fragment
                Toast.makeText(requireContext(), getString(R.string.alert_dialog__confirmation),
                    Toast.LENGTH_LONG).show()
            }
            registerErrorLiveData.observe(viewLifecycleOwner) {
                //TODO show correctly registration error messages
                Toast.makeText(requireContext(), getString(it.errorMessage), Toast.LENGTH_LONG).show()
            }
        }
    }


    companion object {
        fun newInstance() = CreatePersonalAccountPasswordFragment()
    }
}
