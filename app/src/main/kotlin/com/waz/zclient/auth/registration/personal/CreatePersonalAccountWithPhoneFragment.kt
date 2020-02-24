package com.waz.zclient.auth.registration.personal

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import kotlinx.android.synthetic.main.fragment_create_personal_account_with_email.*

class CreatePersonalAccountWithPhoneFragment : Fragment(R.layout.fragment_create_personal_account_with_phone) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        confirmationButton.isEnabled = false
    }

    companion object {
        fun newInstance() = CreatePersonalAccountWithPhoneFragment()
    }
}
