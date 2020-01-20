package com.waz.zclient.settings.account.edithandle

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
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.user.domain.usecase.handle.*
import kotlinx.android.synthetic.main.fragment_edit_handle_dialog.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleFragment : DialogFragment() {

    private val editHandleViewModel: EditHandleViewModel by viewModel()

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
        initViewModel()
        initHandleInput()
        initBackButton()
        initOkButton()
    }

    private fun initHandleInput() {
        updateHandleText(suggestedHandle)
        edit_handle_edit_text.doAfterTextChanged {
            editHandleViewModel.afterHandleTextChanged(it.toString())
        }
    }

    private fun initOkButton() {
        edit_handle_ok_button.setOnClickListener {
            editHandleViewModel.onOkButtonClicked(edit_handle_edit_text.text.toString())
        }
    }

    private fun initBackButton() {
        edit_handle_back_button.setOnClickListener {
            editHandleViewModel.onBackButtonClicked(suggestedHandle)
        }
    }

    private fun initViewModel() {
        with(editHandleViewModel) {
            success.observe(viewLifecycleOwner) { updateSuccessMessage() }
            error.observe(viewLifecycleOwner) { updateErrorMessage(it) }
            okEnabled.observe(viewLifecycleOwner) { edit_handle_ok_button.isEnabled = it }
            dismiss.observe(viewLifecycleOwner) { dismiss() }
        }
    }

    private fun updateSuccessMessage() {
        edit_handle_edit_text_container.setErrorTextAppearance(R.style.InputHandle_Green)
    }

    private fun updateErrorMessage(error: ValidateHandleError) {
        edit_handle_edit_text_container.setErrorTextAppearance(R.style.InputHandle_Red)
        edit_handle_edit_text_container.error = when (error) {
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
        edit_handle_edit_text.startAnimation(AnimationUtils.loadAnimation(context, R.anim.shake_animation))
    }

    private fun updateHandleText(handle: String) {
        edit_handle_edit_text.setText(handle)
        edit_handle_edit_text.setSelection(edit_handle_edit_text.length())
    }

    companion object {
        private const val CURRENT_HANDLE_BUNDLE_KEY = "currentHandleBundleKey"

        fun newInstance(currentHandle: String):
            EditHandleFragment = EditHandleFragment()
            .withArgs {
                putString(CURRENT_HANDLE_BUNDLE_KEY, currentHandle)
            }
    }
}
