package com.waz.zclient.auth.signup

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import kotlinx.android.synthetic.main.fragment_create_account.*

class CreateAccountFragment : Fragment(R.layout.fragment_create_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initCreatePersonalAccount()
        initCreateProAccount()
    }

    private fun initCreatePersonalAccount() {
        createPersonalAccountLayoutContainer.setOnClickListener {
            replaceFragment(CreatePersonalAccountFragment.newInstance())
        }
    }

    private fun initCreateProAccount() {
        createProAccountLayoutContainer.setOnClickListener {
            //TODO call CreateProAccountFragment once ready
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        (activity as AppCompatActivity).replaceFragment(R.id.activityCreateAccountLayoutContainer, fragment)
    }

    companion object {
        fun newInstance() = CreateAccountFragment()
    }
}
