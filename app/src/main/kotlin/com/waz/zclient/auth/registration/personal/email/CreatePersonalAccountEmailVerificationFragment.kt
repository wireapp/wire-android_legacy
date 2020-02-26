package com.waz.zclient.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R

class CreatePersonalAccountEmailVerificationFragment : Fragment(R.layout.fragment_create_personal_account_email_verification) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    companion object {
        fun newInstance() = CreatePersonalAccountEmailVerificationFragment()
    }
}
