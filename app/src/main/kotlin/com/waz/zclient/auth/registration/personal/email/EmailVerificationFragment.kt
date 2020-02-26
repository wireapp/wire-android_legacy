package com.waz.zclient.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R

class EmailVerificationFragment : Fragment(R.layout.fragment_email_verification) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        fun newInstance() = EmailVerificationFragment()
    }
}
