package com.waz.zclient.auth.registration.personal

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import kotlinx.android.synthetic.main.fragment_create_personal_account_with_email.*
import org.koin.android.viewmodel.ext.android.viewModel

class CreatePersonalAccountWithEmailFragment : Fragment(R.layout.fragment_create_personal_account_with_email) {

    private val createPersonalAccountViewModel: CreatePersonalAccountViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel()
        initEmailChangedListener()
        updateConfirmationButtonStatus(false)
    }

    private fun initViewModel() {
        with(createPersonalAccountViewModel) {
            successLiveData.observe(viewLifecycleOwner) { updateConfirmationButtonStatus(true) }
            errorLiveData.observe(viewLifecycleOwner) { updateConfirmationButtonStatus(false) }
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

    companion object {
        fun newInstance() = CreatePersonalAccountWithEmailFragment()
    }
}
