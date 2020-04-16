package com.waz.zclient.feature.settings.account.edithandle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.feature.settings.di.SETTINGS_SCOPE_ID
import com.waz.zclient.shared.user.handle.HandleAlreadyExists
import com.waz.zclient.shared.user.handle.HandleInvalid
import com.waz.zclient.shared.user.handle.HandleTooShort
import com.waz.zclient.shared.user.handle.UnknownError
import com.waz.zclient.shared.user.handle.ValidateHandleError
import kotlinx.android.synthetic.main.fragment_edit_handle_dialog.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleDialogFragment : DialogFragment() {

    private val editHandleViewModel by viewModel<SettingsAccountEditHandleViewModel>(SETTINGS_SCOPE_ID)

    private val suggestedHandle: String by lazy {
        arguments?.getString(CURRENT_HANDLE_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dark_Preferences)
        isCancelable = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_edit_handle_dialog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initHandleInput()
        initBackButton()
        initOkButton()
        observeHandleSuccessData()
        observeHandleErrorData()
        observeDismissData()
    }

    private fun initHandleInput() {
        updateHandleText(suggestedHandle)
        editHandleViewModel.handleLiveData.observe(viewLifecycleOwner) {
            updateHandleText(it)
        }

        editHandleDialogHandleEditText.doAfterTextChanged {
            editHandleViewModel.afterHandleTextChanged(it.toString())
        }
    }

    private fun initOkButton() {
        editHandleViewModel.okEnabledLiveData.observe(viewLifecycleOwner) {
            editHandleDialogOkButton.isEnabled = it
        }
        editHandleDialogOkButton.setOnClickListener {
            editHandleViewModel.onOkButtonClicked(editHandleDialogHandleEditText.text.toString())
        }
    }

    private fun initBackButton() {
        editHandleDialogBackButton.setOnClickListener {
            editHandleViewModel.onBackButtonClicked(suggestedHandle)
        }
    }

    private fun observeHandleSuccessData() {
        editHandleViewModel.successLiveData.observe(viewLifecycleOwner) { updateSuccessMessage() }
    }

    private fun observeHandleErrorData() {
        editHandleViewModel.errorLiveData.observe(viewLifecycleOwner) { updateErrorMessage(it) }
    }

    private fun observeDismissData() {
        editHandleViewModel.dismissLiveData.observe(viewLifecycleOwner) { dismiss() }
    }

    private fun updateSuccessMessage() {
        editHandleDialogHandleTextInputLayout.error = String.empty()
    }

    private fun updateErrorMessage(error: ValidateHandleError) {
        editHandleDialogHandleTextInputLayout.error = when (error) {
            is HandleAlreadyExists -> getString(R.string.edit_account_handle_error_already_taken)
            is HandleTooShort -> getString(R.string.edit_account_handle_error_too_short)
            is HandleInvalid -> getString(R.string.edit_account_handle_error_invalid_characters)
            is UnknownError -> getString(R.string.edit_account_handle_error_unknown_error)
            else -> String.empty()
        }

        when (error) {
            is HandleInvalid, UnknownError -> shakeInputField()
        }
    }

    private fun shakeInputField() {
        editHandleDialogHandleEditText.startAnimation(AnimationUtils.loadAnimation(context, R.anim.shake_animation))
    }

    private fun updateHandleText(handle: String) {
        editHandleDialogHandleEditText.setText(handle)
        editHandleDialogHandleEditText.setSelection(editHandleDialogHandleEditText.length())
    }

    companion object {
        private const val CURRENT_HANDLE_BUNDLE_KEY = "currentHandleBundleKey"

        fun newInstance(currentHandle: String):
            EditHandleDialogFragment = EditHandleDialogFragment()
            .withArgs { putString(CURRENT_HANDLE_BUNDLE_KEY, currentHandle) }
    }
}
