package com.waz.zclient.feature.auth.registration.personal.email.name

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.showKeyboard
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountEmailCredentialsViewModel
import com.waz.zclient.feature.auth.registration.personal.email.password.CreatePersonalAccountPasswordFragment
import kotlinx.android.synthetic.main.fragment_create_personal_account_name.*

class CreatePersonalAccountEmailNameFragment : Fragment(R.layout.fragment_create_personal_account_name) {

    private val nameViewModel: CreatePersonalAccountEmailNameViewModel
        by viewModel(REGISTRATION_SCOPE_ID)

    private val emailCredentialsViewModel: CreatePersonalAccountEmailCredentialsViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeNameValidationData()
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
            saveName()
            showPasswordScreen()
        }
    }

    private fun showPasswordScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountPasswordFragment.newInstance()
        )
    }

    private fun saveName() {
        emailCredentialsViewModel.saveName(createPersonalAccountNameEditText.text.toString())
    }

    companion object {
        fun newInstance() = CreatePersonalAccountEmailNameFragment()
    }
}
