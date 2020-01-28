package com.waz.zclient.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.invisible
import com.waz.zclient.core.extension.startActivityWithAction
import com.waz.zclient.core.extension.visible
import kotlinx.android.synthetic.main.fragment_welcome.*

class WelcomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initCreateAccountButtonListener()
        initLoginButtonListener()
        initEnterpriseLoginButtonListener()
        configureEnterpriseLoginVisibility()
    }

    private fun initCreateAccountButtonListener() {
        welcomeCreateAccountButton.setOnClickListener { startCreateAccountFlow() }
    }

    private fun initLoginButtonListener() {
        welcomeLoginButton.setOnClickListener { startLoginFlow() }
    }

    private fun initEnterpriseLoginButtonListener() {
        welcomeEnterpriseLoginButton.setOnClickListener { startEnterpriseLoginFlow() }
    }

    private fun startCreateAccountFlow() {
        startActivityWithAction(ACTION_CREATE_ACCOUNT)
    }

    private fun startLoginFlow() {
        startActivityWithAction(ACTION_LOGIN)
    }

    private fun startEnterpriseLoginFlow() {
        startActivityWithAction(ACTION_SSO_LOGIN)
    }

    private fun configureEnterpriseLoginVisibility() {
        if (Config.allowSso()) welcomeEnterpriseLoginButton.visible()
        else welcomeEnterpriseLoginButton.invisible()
    }
    companion object {
        fun newInstance() = WelcomeFragment()

        private val ACTION_LOGIN = Config.applicationId() + ".LOGIN_ACTION"
        private val ACTION_CREATE_ACCOUNT = Config.applicationId() + ".CREATE_ACCOUNT_ACTION"
        private val ACTION_SSO_LOGIN = Config.applicationId() + ".SSO_LOGIN_ACTION"
    }
}
