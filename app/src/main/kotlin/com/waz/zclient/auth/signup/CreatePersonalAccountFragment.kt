package com.waz.zclient.auth.signup

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R

class CreatePersonalAccountFragment : Fragment(R.layout.fragment_create_personal_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    companion object {
        fun newInstance() = CreatePersonalAccountFragment()
    }
}
