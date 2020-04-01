package com.waz.zclient.feature.auth.registration.personal.phone

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R

class CreatePersonalAccountWithPhoneFragment : Fragment(R.layout.fragment_create_personal_account_with_phone) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        fun newInstance() = CreatePersonalAccountWithPhoneFragment()
    }
}
