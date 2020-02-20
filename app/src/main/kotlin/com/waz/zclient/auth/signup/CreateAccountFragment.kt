package com.waz.zclient.auth.signup

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import kotlinx.android.synthetic.main.fragment_create_account.*

class CreateAccountFragment : Fragment(R.layout.fragment_create_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initCreatePersonalAccount()
        initCreateProAccount()
    }

    private fun initCreatePersonalAccount() {
        createPersonalAccountLayoutContainer.setOnClickListener {
            //TODO call CreatePersonalAccountFragment once ready
        }
    }

    private fun initCreateProAccount() {
        createProAccountLayoutContainer.setOnClickListener {
            //TODO call CreateProAccountFragment once ready
        }
    }

    companion object {
        fun newInstance() = CreateAccountFragment()
    }
}
