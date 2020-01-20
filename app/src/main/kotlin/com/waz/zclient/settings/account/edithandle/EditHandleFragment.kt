package com.waz.zclient.settings.account.edithandle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doBeforeTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.user.domain.usecase.handle.HandleExistsAlreadyError
import com.waz.zclient.user.domain.usecase.handle.HandleInvalidError
import com.waz.zclient.user.domain.usecase.handle.HandleUnknownError
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleError
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
        edit_handle_edit_text.doBeforeTextChanged { text, _, _, _ ->
            editHandleViewModel.beforeHandleTextChanged(text.toString())
        }
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
            handle.observe(viewLifecycleOwner) { updateHandleText(it) }
            error.observe(viewLifecycleOwner) { updateErrorMessage(it) }
            okEnabled.observe(viewLifecycleOwner) { edit_handle_ok_button.isEnabled = it }
            dismiss.observe(viewLifecycleOwner) { dismiss() }
        }
    }

    private fun updateErrorMessage(error: ValidateHandleError) {
        edit_handle_edit_text_container.error = when (error) {
            is HandleExistsAlreadyError ->
                getString(R.string.pref__account_action__dialog__change_username__error_already_taken)
            is HandleUnknownError ->
                getString(R.string.pref__account_action__dialog__change_username__error_unknown)
            else -> String.empty()
        }
        when (error) {
            is HandleUnknownError, HandleInvalidError -> shakeInputField()
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
