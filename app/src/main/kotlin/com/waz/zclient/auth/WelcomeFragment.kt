package com.waz.zclient.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.config.Config
import kotlinx.android.synthetic.main.fragment_welcome.*


class WelcomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        welcomeCreateAccountButton.setOnClickListener { startCreateAccountFlow() }
        welcomeLoginButton.setOnClickListener { startLoginFlow() }
        welcomeEnterpriseLoginButton.setOnClickListener { startSsoLoginFlow() }
        configureSSOButtonVisibility()
    }

    private fun startCreateAccountFlow() {
        val createAccountIntent = Intent().apply { action = ACTION_CREATE_ACCOUNT }
        startActivity(createAccountIntent)
    }

    private fun startLoginFlow() {
        val loginIntent = Intent().apply {
            action = ACTION_LOGIN
            putExtra(BUNDLE_KEY_FRAGMENT_TO_START, BUNDLE_VALUE_LOGIN_FRAGMENT)
        }
        startActivity(loginIntent)
    }

    private fun startSsoLoginFlow() {
        val loginIntent = Intent().apply {
            action = ACTION_SSO_LOGIN
        }
        startActivity(loginIntent)
    }

    private fun configureSSOButtonVisibility() {
        when (Config.allowSso()) {
            true -> welcomeEnterpriseLoginButton.visibility = VISIBLE
            false -> welcomeEnterpriseLoginButton.visibility = INVISIBLE
        }
    }

    companion object {
        fun newInstance() = WelcomeFragment()
        private val ACTION_LOGIN = Config.applicationId() + ".LOGIN_ACTION"
        private val ACTION_CREATE_ACCOUNT = Config.applicationId() + ".CREATE_ACCOUNT_ACTION"
        private val ACTION_SSO_LOGIN = Config.applicationId() + ".SSO_LOGIN_ACTION"
        const val BUNDLE_KEY_FRAGMENT_TO_START = "fragmentToStart"
        const val BUNDLE_VALUE_LOGIN_FRAGMENT = "LoginFragment"
    }
}
