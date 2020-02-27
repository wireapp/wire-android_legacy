package com.waz.zclient.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.showKeyboard
import com.waz.zclient.core.extension.withArgs
import kotlinx.android.synthetic.main.fragment_email_verification.*

class EmailVerificationFragment : Fragment(R.layout.fragment_email_verification) {

    private val email: String by lazy {
        arguments?.getString(EMAIL_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initDescriptionTextView()
        initFirstDigitEditText()
        initSecondDigitEditText()
        initThirdDigitEditText()
        initFourthDigitEditText()
        initFifthDigitEditText()
    }

    private fun initDescriptionTextView() {
        emailVerificationDescriptionTextView.text = String.format(getString(R.string.email_verification_description), email)
    }

    private fun initFirstDigitEditText() {
        emailVerificationFirstDigitEditText.requestFocus()
        emailVerificationFirstDigitEditText.showKeyboard()
        emailVerificationFirstDigitEditText.doAfterTextChanged { emailVerificationSecondDigitEditText.requestFocus() }
    }

    private fun initSecondDigitEditText() {
        emailVerificationSecondDigitEditText.doAfterTextChanged { emailVerificationThirdDigitEditText.requestFocus() }
    }

    private fun initThirdDigitEditText() {
        emailVerificationThirdDigitEditText.doAfterTextChanged { emailVerificationFourthDigitEditText.requestFocus() }
    }

    private fun initFourthDigitEditText() {
        emailVerificationFourthDigitEditText.doAfterTextChanged { emailVerificationFifthDigitEditText.requestFocus() }
    }

    private fun initFifthDigitEditText() {
        emailVerificationFifthDigitEditText.doAfterTextChanged { emailVerificationSixthDigitEditText.requestFocus() }
    }

    companion object {
        private const val EMAIL_BUNDLE_KEY = "emailBundleKey"

        fun newInstance(email: String) = EmailVerificationFragment()
            .withArgs { putString(EMAIL_BUNDLE_KEY, email) }
    }
}
