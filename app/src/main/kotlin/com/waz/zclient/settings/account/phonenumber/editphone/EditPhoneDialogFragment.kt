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
import com.waz.zclient.core.permissions.PermissionManagerFactory
import com.waz.zclient.core.permissions.extension.strictRequestReadPhoneState
import kotlinx.android.synthetic.main.dialog_edit_phone.editPhoneCountryCodeEditText
import kotlinx.android.synthetic.main.dialog_edit_phone.editPhonePhoneNumberEditText
import org.koin.android.viewmodel.ext.android.viewModel

class EditPhoneDialogFragment : DialogFragment() {

    private val permissionManager by lazy {
        PermissionManagerFactory.getPermissionManager(this)
    }

    private val rootView: View by lazy {
        requireActivity().layoutInflater.inflate(R.layout.dialog_edit_phone, null)
    }

    private val editPhoneNumberViewModel: EditPhoneNumberViewModel by viewModel()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.pref__account_action__dialog__edit_phone__title))
            .setView(rootView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                editPhoneNumberViewModel.onOkButtonClicked(
                    editPhoneCountryCodeEditText.text.toString(),
                    editPhonePhoneNumberEditText.text.toString()
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                editPhoneNumberViewModel.onCancelButtonClicked()
            }.create()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        initPhoneInput()
        initError()
    }

    private fun initError() {
        editPhoneNumberViewModel.errorLiveData.observe(viewLifecycleOwner) {

        }
    }

    private fun initPhoneInput() {
        editPhoneNumberViewModel.phoneNumberLiveData.observe(viewLifecycleOwner) {
            showConfirmationDialog(it)
        }
    }

    private fun showConfirmationDialog(phoneNumber: String) {
        //Show confirmation dialog here
    }

    override fun onStart() {
        super.onStart()
        permissionManager.strictRequestReadPhoneState {
            it.fold(
                editPhoneNumberViewModel::onReadPhonePermissionDenied,
                editPhoneNumberViewModel::onReadPhonePermissionGranted
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {

        private const val CURRENT_PHONE_NUMBER_KEY = "currentPhoneNumber"

        fun newInstance(phoneNumber: String) =
            EditPhoneDialogFragment().withArgs {
                putString(CURRENT_PHONE_NUMBER_KEY, phoneNumber)
            }
    }
}
