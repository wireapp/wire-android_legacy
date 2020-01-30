package com.waz.zclient.settings.account.phonenumber.editphone

import android.app.Dialog
import android.content.DialogInterface
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

    private val settingsAccountPhoneNumberViewModel: SettingsAccountPhoneNumberViewModel by viewModel()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.pref__account_action__dialog__edit_phone__title))
            .setView(rootView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (hasEmail) {
            builder.setNeutralButton(R.string.pref_account_delete, null)
        }

        return builder.create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        initPhoneInput()
        initCountryCodeInput()
        initDeleteNumberButton()

        lifecycleScope.launchWhenResumed {
            settingsAccountPhoneNumberViewModel.loadPhoneNumberData(phoneNumber)
        }
        return rootView
    }

    override fun onStart() {
        super.onStart()
        initButtonActions()
    }

    private fun initButtonActions() {
        val alertDialog = dialog
        if (alertDialog is AlertDialog) {
            val confirmButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            confirmButton.setOnClickListener {
                confirmPhoneNumber()
            }

            val deleteButton = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            deleteButton.setOnClickListener {
                settingsAccountPhoneNumberViewModel.onDeleteNumberButtonClicked(
                    rootView.editPhoneDialogCountryCodeTextInputEditText.text.toString(),
                    rootView.editPhoneDialogPhoneNumberTextInputEditText.text.toString()
                )
            }
        }
    }

    private fun initDeleteNumberButton() {
        settingsAccountPhoneNumberViewModel.deleteNumberLiveData.observe(viewLifecycleOwner) {
            showDeleteNumberDialog(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun initCountryCodeInput() {
        settingsAccountPhoneNumberViewModel.countryCodeLiveData.observe(viewLifecycleOwner) {
            rootView.editPhoneDialogCountryCodeTextInputEditText.setText(it)
        }
        settingsAccountPhoneNumberViewModel.countryCodeErrorLiveData.observe(viewLifecycleOwner) {
            updateCountryCodeError(getString(it.errorMessage))
        }
    }

    private fun initPhoneInput() {
        rootView.editPhoneDialogPhoneNumberTextInputEditText.requestFocus()
        rootView.editPhoneDialogPhoneNumberTextInputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmPhoneNumber()
                true
            } else false
        }

        with(settingsAccountPhoneNumberViewModel) {
            phoneNumberLiveData.observe(viewLifecycleOwner) {
                rootView.editPhoneDialogPhoneNumberTextInputEditText.setText(it)
            }
            phoneNumberErrorLiveData.observe(viewLifecycleOwner) {
                updatePhoneNumberError(getString(it.errorMessage))
            }
            confirmationLiveData.observe(viewLifecycleOwner) {
                showConfirmationDialog(it)
            }
        }
    }

    private fun confirmPhoneNumber() {
        settingsAccountPhoneNumberViewModel.onNumberConfirmed(
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

    private fun showDeleteNumberDialog(phoneNumber: String) {
        dismiss()
        DeletePhoneDialogFragment.newInstance(phoneNumber)
            .show(requireActivity().supportFragmentManager, "DeletePhone")
    }

    private fun showConfirmationDialog(phoneNumber: String) {
        dismiss()
        UpdatePhoneDialogFragment.newInstance(phoneNumber)
            .show(requireActivity().supportFragmentManager, "ConfirmPhone")
    }

    companion object {

        private const val CURRENT_PHONE_NUMBER_KEY = "currentPhoneNumber"
        private const val HAS_EMAIL_BUNDLE_KEY = "hasEmailBundleKey"

        fun newInstance(
            phoneNumber: String,
            hasEmail: Boolean
        ) =
            EditPhoneDialogFragment().withArgs {
                putString(CURRENT_PHONE_NUMBER_KEY, phoneNumber)
                putBoolean(HAS_EMAIL_BUNDLE_KEY, hasEmail)
            }
    }
}
