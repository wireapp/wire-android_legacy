package com.waz.zclient.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import kotlinx.android.synthetic.main.fragment_welcome.*


class WelcomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        create_account_button.setOnClickListener { createAccount() }
        login_button.setOnClickListener { login() }
        enterprise_login_button.setOnClickListener { }
    }

    private fun createAccount() {
        val createAccountIntent = Intent().apply { action = "com.waz.zclient.CREATE_ACCOUNT_ACTION" }
        startActivity(createAccountIntent)
    }

    private fun login() {
        val loginIntent = Intent().apply {
            action = "com.waz.zclient.LOGIN_ACTION"
            putExtra(BUNDLE_KEY_FRAGMENT_TO_START, BUNDLE_VALUE_LOGIN_FRAGMENT)
        }
        startActivity(loginIntent)
    }

    companion object {
        fun newInstance() = WelcomeFragment()
        const val BUNDLE_KEY_FRAGMENT_TO_START = "fragmentToStart"
        const val BUNDLE_VALUE_LOGIN_FRAGMENT = "LoginFragment"
    }
}
