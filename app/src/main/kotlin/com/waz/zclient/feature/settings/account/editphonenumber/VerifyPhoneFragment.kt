package com.waz.zclient.feature.settings.account.editphonenumber

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.withArgs

class VerifyPhoneFragment : Fragment() {

    private val phoneNumber: String by lazy {
        arguments?.getString(CONFIRMED_PHONE_NUMBER_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        Toast.makeText(requireContext(), "Verifying phonumber$phoneNumber", Toast.LENGTH_LONG).show()
        return view
    }

    companion object {
        private const val CONFIRMED_PHONE_NUMBER_BUNDLE_KEY = "phoneNumberBundleKey"

        fun newInstance(phoneNumber: String) =
            VerifyPhoneFragment().withArgs {
                putString(CONFIRMED_PHONE_NUMBER_BUNDLE_KEY, phoneNumber)
            }
    }
}
