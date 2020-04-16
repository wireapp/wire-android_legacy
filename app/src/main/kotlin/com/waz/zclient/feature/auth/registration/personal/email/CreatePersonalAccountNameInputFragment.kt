package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_create_personal_account_email_input.confirmationButton
import kotlinx.android.synthetic.main.fragment_create_personal_account_name_input.*

class CreatePersonalAccountNameInputFragment : Fragment(R.layout.fragment_create_personal_account_name_input) {

    private val createPersonalAccountViewModel: CreatePersonalAccountWithEmailViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initConfirmationButton()
    }

    private fun initConfirmationButton() {
        confirmationButton.setOnClickListener {
            saveName()
            showPasswordInputScreen()
        }
    }

    private fun showPasswordInputScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountPasswordInputFragment.newInstance()
        )
    }

    private fun saveName() {
        createPersonalAccountViewModel.saveName(createPersonalAccountNameInputEditText.text.toString())
    }

    companion object {
        fun newInstance() = CreatePersonalAccountNameInputFragment()
    }
}
