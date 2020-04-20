package com.waz.zclient.feature.settings.account.editphonenumber

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import kotlinx.android.synthetic.main.activity_edit_phone.*

class EditPhoneNumberActivity : AppCompatActivity(R.layout.activity_edit_phone) {

    private val phoneNumber: String by lazy {
        intent.getStringExtra(CURRENT_PHONE_NUMBER_KEY)
    }

    private val hasEmail: Boolean by lazy {
        intent.getBooleanExtra(HAS_EMAIL_BUNDLE_KEY, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(editPhoneActivityToolbar)

        replaceFragment(
            R.id.editPhoneActivityFragmentContainer,
            EditPhoneNumberFragment.newInstance(phoneNumber, hasEmail),
            false
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val CURRENT_PHONE_NUMBER_KEY = "currentPhoneNumber"
        private const val HAS_EMAIL_BUNDLE_KEY = "hasEmailBundleKey"

        @JvmStatic
        fun newIntent(context: Context, phoneNumber: String, hasEmail: Boolean) =
            Intent(context, EditPhoneNumberActivity::class.java).also {
                it.putExtra(CURRENT_PHONE_NUMBER_KEY, phoneNumber)
                it.putExtra(HAS_EMAIL_BUNDLE_KEY, hasEmail)
            }
    }
}
