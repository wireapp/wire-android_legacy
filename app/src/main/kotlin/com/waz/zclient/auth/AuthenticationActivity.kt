package com.waz.zclient.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment

class AuthenticationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authentication)
        replaceFragment(R.id.layout_container, WelcomeFragment.newInstance(), false)
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context) = Intent(context, AuthenticationActivity::class.java)
    }
}
