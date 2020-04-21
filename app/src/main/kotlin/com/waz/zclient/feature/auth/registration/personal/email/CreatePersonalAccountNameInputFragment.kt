package com.waz.zclient.feature.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import kotlinx.android.synthetic.main.fragment_create_personal_account_email_input.confirmationButton

class CreatePersonalAccountNameInputFragment : Fragment(R.layout.fragment_create_personal_account_name_input) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initConfirmationButton()
    }

    private fun initConfirmationButton() {
        confirmationButton.setOnClickListener {
            showPasswordInputScreen()
        }
    }

    private fun showPasswordInputScreen() {
        replaceFragment(
            R.id.activityCreateAccountLayoutContainer,
            CreatePersonalAccountPasswordInputFragment.newInstance()
        )
    }

    companion object {
        fun newInstance() = CreatePersonalAccountNameInputFragment()
    }
}
