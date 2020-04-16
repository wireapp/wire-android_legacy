package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_create_personal_account_email_input.confirmationButton
import kotlinx.android.synthetic.main.fragment_create_personal_account_password_input.*

class CreatePersonalAccountPasswordInputFragment : Fragment(R.layout.fragment_create_personal_account_password_input) {

    private val createPersonalAccountViewModel: CreatePersonalAccountWithEmailViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initConfirmationButton()
        observeRegistrationData()
    }

    private fun initConfirmationButton() {
        confirmationButton.setOnClickListener {
            registerNewUser()
        }
    }

    private fun registerNewUser() {
        createPersonalAccountViewModel.register(createPersonalAccountPasswordInputEditText.text.toString())
    }

    private fun observeRegistrationData() {
        with(createPersonalAccountViewModel) {
            registerSuccessLiveData.observe(viewLifecycleOwner) {
                Toast.makeText(requireContext(), "OK", Toast.LENGTH_LONG).show()
            }
            registerErrorLiveData.observe(viewLifecycleOwner) {
                Toast.makeText(requireContext(), getString(it.errorMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        fun newInstance() = CreatePersonalAccountPasswordInputFragment()
    }
}
