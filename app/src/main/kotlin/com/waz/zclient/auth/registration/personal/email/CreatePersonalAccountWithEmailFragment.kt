package com.waz.zclient.auth.registration.personal.email

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.auth.registration.personal.CreatePersonalAccountViewModel
import com.waz.zclient.core.extension.replaceFragment
import kotlinx.android.synthetic.main.fragment_create_personal_account_with_email.*
import org.koin.android.viewmodel.ext.android.viewModel

class CreatePersonalAccountWithEmailFragment : Fragment(R.layout.fragment_create_personal_account_with_email) {

    private val createPersonalAccountViewModel: CreatePersonalAccountViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel()
        initEmailChangedListener()
        initConfirmationButton()
    }

    private fun initViewModel() {
        with(createPersonalAccountViewModel) {
            confirmationButtonEnabledLiveData.observe(viewLifecycleOwner) { updateConfirmationButtonStatus(it) }
        }
    }

    private fun updateConfirmationButtonStatus(enabled: Boolean) {
        confirmationButton.isEnabled = enabled
    }

    private fun initEmailChangedListener() {
        createPersonalAccountWithEmailEditText.doAfterTextChanged {
            createPersonalAccountViewModel.validateEmail(it.toString())
        }
    }

    private fun initConfirmationButton() {
        updateConfirmationButtonStatus(false)
        confirmationButton.setOnClickListener {
            replaceFragment(EmailVerificationFragment.newInstance())
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        (activity as AppCompatActivity).replaceFragment(R.id.activityCreateAccountLayoutContainer, fragment)
    }

    companion object {
        fun newInstance() = CreatePersonalAccountWithEmailFragment()
    }
}
