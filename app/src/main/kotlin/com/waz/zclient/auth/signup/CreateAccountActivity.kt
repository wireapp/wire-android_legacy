package com.waz.zclient.auth.signup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment

class CreateAccountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)
        replaceFragment(R.id.activityCreateAccountLayoutContainer, CreateAccountFragment.newInstance(),false)
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context) = Intent(context, CreateAccountActivity::class.java)
    }
}
