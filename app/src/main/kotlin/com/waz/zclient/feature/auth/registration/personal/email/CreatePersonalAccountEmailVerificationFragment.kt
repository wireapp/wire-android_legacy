package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.showKeyboard
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_create_personal_account_email_verification.*

class CreatePersonalAccountEmailVerificationFragment : Fragment(
    R.layout.fragment_create_personal_account_email_verification
) {

    //TODO handle no internet connections status
    private val createPersonalAccountViewModel: CreatePersonalAccountWithEmailViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val email: String by lazy {
        arguments?.getString(EMAIL_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initDescriptionTextView()
        initVerificationCodeInput()
        observeActivateEmailData()
    }

    private fun initDescriptionTextView() {
        emailVerificationDescriptionTextView.text =
            getString(R.string.email_verification_description, email)
    }

    private fun initVerificationCodeInput() {
        //TODO handle all the cases for keyboard actions
        emailVerificationFirstDigitEditText.requestFocus()
        emailVerificationFirstDigitEditText.showKeyboard()
        emailVerificationFirstDigitEditText.doAfterTextChanged {
            emailVerificationSecondDigitEditText.requestFocus()
        }
        emailVerificationSecondDigitEditText.doAfterTextChanged {
            emailVerificationThirdDigitEditText.requestFocus()
        }
        emailVerificationThirdDigitEditText.doAfterTextChanged {
            emailVerificationFourthDigitEditText.requestFocus()
        }
        emailVerificationFourthDigitEditText.doAfterTextChanged {
            emailVerificationFifthDigitEditText.requestFocus()
        }
        emailVerificationFifthDigitEditText.doAfterTextChanged {
            emailVerificationSixthDigitEditText.requestFocus()
        }
        emailVerificationSixthDigitEditText.doAfterTextChanged {
            createPersonalAccountViewModel.activateEmail(email, verificationCode())
        }
    }

    private fun verificationCode(): String = emailVerificationFirstDigitEditText.text.toString() +
        emailVerificationSecondDigitEditText.text.toString() +
        emailVerificationThirdDigitEditText.text.toString() +
        emailVerificationFourthDigitEditText.text.toString() +
        emailVerificationFifthDigitEditText.text.toString() +
        emailVerificationSixthDigitEditText.text.toString()

    private fun observeActivateEmailData() {
        with(createPersonalAccountViewModel) {
            activateEmailSuccessLiveData.observe(viewLifecycleOwner) {
                showEnterNameScreen()
            }
            activateEmailErrorLiveData.observe(viewLifecycleOwner) {
                showInvalidCodeError(getString(it.errorMessage))
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

    companion object {
        private const val EMAIL_BUNDLE_KEY = "emailBundleKey"

        fun newInstance(email: String) = CreatePersonalAccountEmailVerificationFragment()
            .withArgs { putString(EMAIL_BUNDLE_KEY, email) }
    }
}
