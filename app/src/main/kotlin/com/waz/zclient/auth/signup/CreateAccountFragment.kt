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
        initCreateTeamAccount()
    }

    private fun initCreatePersonalAccount() {
        createPersonalAccountLayoutContainer.setOnClickListener {
            //val inputMethod = if (LayoutSpec.isPhone(getContext)) Phone else Email
            //parentActivity.showFragment(SignInFragment(SignInMethod(Register, inputMethod)), SignInFragment.Tag)
        }
    }

    private fun initCreateTeamAccount() {
        createProAccountLayoutContainer.setOnClickListener {
           // parentActivity.showFragment(TeamNameFragment(), TeamNameFragment.Tag)
        }
    }

    companion object {
        fun newInstance() = CreateAccountFragment()
    }
}
