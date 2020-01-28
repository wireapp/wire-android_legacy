package com.waz.zclient.settings.account.phonenumber.editphone

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs
import kotlinx.android.synthetic.main.dialog_fragment_edit_phone.*
import kotlinx.android.synthetic.main.dialog_fragment_edit_phone.view.*
import org.koin.android.viewmodel.ext.android.viewModel

class EditPhoneDialogFragment : DialogFragment() {

    private val rootView: View by lazy {
        requireActivity().layoutInflater.inflate(R.layout.dialog_fragment_edit_phone, null)
    }

    private val editPhoneNumberViewModel: EditPhoneNumberViewModel by viewModel()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.pref__account_action__dialog__edit_phone__title))
            .setView(rootView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                editPhoneNumberViewModel.onOkButtonClicked(
                    rootView.editPhoneDialogCountryCodeTextInputEditText.text.toString(),
                    rootView.editPhoneDialogPhoneNumberTextInputEditText.text.toString()
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                editPhoneNumberViewModel.onCancelButtonClicked()
            }.create()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        initPhoneInput()
        initCountryCodeInput()
    }

    private fun initCountryCodeInput() {
        editPhoneNumberViewModel.countryCodeErrorLiveData.observe(viewLifecycleOwner) {
            updateCountryCodeError(getString(it.errorMessage))
        }
    }

    private fun initPhoneInput() {
        editPhoneNumberViewModel.phoneNumberErrorLiveData.observe(viewLifecycleOwner) {
            updatePhoneNumberError(getString(it.errorMessage))
        }
        editPhoneNumberViewModel.phoneNumberLiveData.observe(viewLifecycleOwner) {
            showConfirmationDialog(it)
        }
    }

    private fun updateCountryCodeError(errorMessage: String) {
        editPhoneDialogPhoneNumberTextInputLayout.error = errorMessage
    }

    private fun updatePhoneNumberError(errorMessage: String) {
        editPhoneDialogPhoneNumberTextInputLayout.error = errorMessage
    }

    private fun showConfirmationDialog(phoneNumber: String) {
        //Show confirmation dialog here
    }

    companion object {

        private const val CURRENT_PHONE_NUMBER_KEY = "currentPhoneNumber"

        fun newInstance(phoneNumber: String) =
            EditPhoneDialogFragment().withArgs {
                putString(CURRENT_PHONE_NUMBER_KEY, phoneNumber)
            }
    }
}
