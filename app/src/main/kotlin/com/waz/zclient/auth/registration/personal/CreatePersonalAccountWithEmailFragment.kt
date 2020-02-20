package com.waz.zclient.auth.registration.personal

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R

class CreatePersonalAccountWithEmailFragment : Fragment(R.layout.fragment_create_personal_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    companion object {
        fun newInstance() = CreatePersonalAccountWithEmailFragment()
    }
}
