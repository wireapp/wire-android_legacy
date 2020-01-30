package com.waz.zclient.settings.account.phonenumber.editphone

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.withArgs
import kotlinx.android.synthetic.main.dialog_fragment_edit_phone.*
import kotlinx.android.synthetic.main.dialog_fragment_edit_phone.view.*
import org.koin.android.viewmodel.ext.android.viewModel

class EditPhoneDialogFragment : DialogFragment() {

    private val rootView: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.dialog_fragment_edit_phone, null)
    }

    private val phoneNumber: String by lazy {
        arguments?.getString(CURRENT_PHONE_NUMBER_KEY, String.empty()) ?: String.empty()
    }

    private val hasEmail: Boolean by lazy {
        arguments?.getBoolean(HAS_EMAIL_BUNDLE_KEY, true) ?: true
    }

    private val editPhoneNumberViewModel: EditPhoneNumberViewModel by viewModel()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.pref__account_action__dialog__edit_phone__title))
            .setView(rootView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                validatePhoneNumber()
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (hasEmail) {
            builder.setNeutralButton(R.string.pref_account_delete) { _, _ ->
                editPhoneNumberViewModel.onDeleteNumberButtonClicked(
                    rootView.editPhoneDialogCountryCodeTextInputEditText.text.toString(),
                    rootView.editPhoneDialogPhoneNumberTextInputEditText.text.toString()
                )
            }
        }

        return builder.create()
    }

    private fun showDeletePhoneNumberDialog() {
        validatePhoneNumber()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        initPhoneInput()
        initCountryCodeInput()
        initDeleteNumberButton()

        lifecycleScope.launchWhenResumed {
            editPhoneNumberViewModel.loadPhoneNumberData(phoneNumber)
        }
        return rootView
    }

    private fun initDeleteNumberButton() {
        editPhoneNumberViewModel.deleteNumberLiveData.observe(viewLifecycleOwner) {
            showDeleteNumberDialog(it)
        }
    }

    private fun showDeleteNumberDialog(it: String) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__title))
            .setMessage(getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__message, phoneNumber))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                editPhoneNumberViewModel.onDeleteNumberButtonConfirmed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun initCountryCodeInput() {
        editPhoneNumberViewModel.countryCodeLiveData.observe(viewLifecycleOwner) {
            rootView.editPhoneDialogCountryCodeTextInputEditText.setText(it)
        }
        editPhoneNumberViewModel.countryCodeErrorLiveData.observe(viewLifecycleOwner) {
            updateCountryCodeError(getString(it.errorMessage))
        }
    }

    private fun initPhoneInput() {
        rootView.editPhoneDialogPhoneNumberTextInputEditText.requestFocus()
        rootView.editPhoneDialogPhoneNumberTextInputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validatePhoneNumber()
                true
            } else false
        }
        editPhoneNumberViewModel.phoneNumberLiveData.observe(viewLifecycleOwner) {
            rootView.editPhoneDialogPhoneNumberTextInputEditText.setText(it)
        }
        editPhoneNumberViewModel.phoneNumberErrorLiveData.observe(viewLifecycleOwner) {
            updatePhoneNumberError(getString(it.errorMessage))
        }
    }

    private fun validatePhoneNumber() {
        editPhoneNumberViewModel.onNumberConfirmed(
            rootView.editPhoneDialogCountryCodeTextInputEditText.text.toString(),
            rootView.editPhoneDialogPhoneNumberTextInputEditText.text.toString()
        )
    }

    private fun updateCountryCodeError(errorMessage: String) {
        editPhoneDialogPhoneNumberTextInputLayout.error = errorMessage
    }

    private fun updatePhoneNumberError(errorMessage: String) {
        editPhoneDialogPhoneNumberTextInputLayout.error = errorMessage
    }

    private fun showConfirmationDialog(phoneNumber: String) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pref__account_action__dialog__add_phone__confirm__title))
            .setMessage(getString(R.string.pref__account_action__dialog__add_phone__confirm__message, phoneNumber))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                editPhoneNumberViewModel.onPhoneNumberConfirmed(phoneNumber)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()    }

    companion object {

        private const val CURRENT_PHONE_NUMBER_KEY = "currentPhoneNumber"
        private const val HAS_EMAIL_BUNDLE_KEY = "hasEmailBundleKey"

        fun newInstance(phoneNumber: String, hasEmail: Boolean) =
            EditPhoneDialogFragment().withArgs {
                putString(CURRENT_PHONE_NUMBER_KEY, phoneNumber)
                putBoolean(HAS_EMAIL_BUNDLE_KEY, hasEmail)
            }
    }
}
