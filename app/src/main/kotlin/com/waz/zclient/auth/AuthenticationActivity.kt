package com.waz.zclient.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.invisible
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.extension.visible
import kotlinx.android.synthetic.main.activity_authentication.*

class AuthenticationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authentication)
        replaceFragment(R.id.layout_container, WelcomeFragment.newInstance(), false)
        configureCloseButtonVisibility(intent.extras.getBoolean(CLOSE_BUTTON_VISIBILITY_BUNDLE_KEY, false))
        initCloseButtonListener()
    }

    private fun configureCloseButtonVisibility(isCloseButtonVisible: Boolean) {
        if (isCloseButtonVisible) {
            authenticationCloseButton.visible()
            authenticationCloseButton.bringToFront()
        } else authenticationCloseButton.invisible()
    }

    private fun initCloseButtonListener() {
        authenticationCloseButton.setOnClickListener { finish() }
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context, isCloseButtonVisible: Boolean): Intent =
            Intent(context, AuthenticationActivity::class.java)
                .putExtra(CLOSE_BUTTON_VISIBILITY_BUNDLE_KEY, isCloseButtonVisible)

        private const val CLOSE_BUTTON_VISIBILITY_BUNDLE_KEY = "isCloseButtonVisible"
    }
}
