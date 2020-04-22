package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.feature.auth.registration.di.REGISTRATION_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_create_personal_account_name.*

class CreatePersonalAccountNameFragment : Fragment(R.layout.fragment_create_personal_account_name) {

    private val createPersonalAccountWithEmailSharedViewModel: CreatePersonalAccountWithEmailSharedViewModel
        by sharedViewModel(REGISTRATION_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initConfirmationButton()
    }

    private fun initConfirmationButton() {
        createPersonalAccountNameConfirmationButton.setOnClickListener {
            saveName()
            showPasswordInputScreen()
        }
    }

    private fun showPasswordInputScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountPasswordFragment.newInstance()
        )
    }

    private fun saveName() {
        createPersonalAccountWithEmailSharedViewModel.saveName(createPersonalAccountNameEditText.text.toString())
    }

    companion object {
        fun newInstance() = CreatePersonalAccountNameFragment()
    }
}
