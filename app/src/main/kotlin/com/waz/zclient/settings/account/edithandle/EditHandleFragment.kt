package com.waz.zclient.settings.account.edithandle

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.observe
import com.google.android.material.textfield.TextInputLayout
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.user.domain.usecase.handle.HandleExistsAlreadyError
import com.waz.zclient.user.domain.usecase.handle.HandleInvalidError
import com.waz.zclient.user.domain.usecase.handle.HandleUnknownError
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleFragment : DialogFragment() {

    companion object {
        private const val CURRENT_HANDLE_BUNDLE_KEY = "currentHandleBundleKey"
        private const val DIALOG_IS_CANCELABLE_BUNDLE_KEY = "dialogIsCancelableBundleKey"

        fun newInstance(currentHandle: String?, handleChangedListener: HandleChangedListener):
            EditHandleFragment = EditHandleFragment()
            .withArgs {
                putString(CURRENT_HANDLE_BUNDLE_KEY, currentHandle)
            }.also {
                it.listener = handleChangedListener
            }
    }

    private val editHandleFragmentViewModel: EditHandleFragmentViewModel by viewModel()

    private lateinit var handleInput: EditText
    private lateinit var handleInputContainer: TextInputLayout
    private lateinit var okButton: View

    private var listener: HandleChangedListener? = null

    interface HandleChangedListener {
        fun onHandleChanged(handle: String)
    }

    private val changeHandleTextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            editHandleFragmentViewModel.afterHandleTextChanged(s.toString())
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            editHandleFragmentViewModel.beforeHandleTextChanged(s.toString())
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // Do nothing
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dark_Preferences)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_edit_handle_dialog, container, false)
        initViews(view)
        initViewModel()
        return view
    }

    private fun initViewModel() {
        with(editHandleFragmentViewModel) {
            handle.observe(viewLifecycleOwner) { updateHandleText(it) }
            error.observe(viewLifecycleOwner) { updateErrorMessage(it) }
            okEnabled.observe(viewLifecycleOwner) { okButton.isEnabled = it }
            dismiss.observe(viewLifecycleOwner) { dismiss() }
        }
    }

    private fun updateErrorMessage(error: ValidateHandleError) {
        when (error) {
            is HandleExistsAlreadyError -> handleInputContainer.error = getString(R.string.pref__account_action__dialog__change_username__error_already_taken)
            is HandleUnknownError -> {
                handleInputContainer.error = getString(R.string.pref__account_action__dialog__change_username__error_unknown)
                shakeInputField()
            }
            is HandleInvalidError -> shakeInputField()
            else -> String.empty()
        }
    }

    private fun shakeInputField() {
        handleInput.startAnimation(AnimationUtils.loadAnimation(context, R.anim.shake_animation))
    }

    private fun updateHandleText(handle: String) {
        handleInput.setText(handle)
        handleInput.setSelection(handleInput.text.length)
    }

    private fun initViews(view: View?) {
        view?.let {
            val suggestedHandle = arguments?.getString(CURRENT_HANDLE_BUNDLE_KEY, String.empty())
            val isCancelable = arguments?.getBoolean(DIALOG_IS_CANCELABLE_BUNDLE_KEY, true) ?: true

            handleInput = it.findViewById(R.id.edit_handle_edit_text)
            handleInput.addTextChangedListener(changeHandleTextWatcher)

            it.findViewById<View>(R.id.edit_handle_ok_button).setOnClickListener {
                editHandleFragmentViewModel.onOkButtonClicked(handleInput.text.toString())
            }

            okButton = it.findViewById<View>(R.id.edit_handle_back_button)
            okButton.setOnClickListener {
                editHandleFragmentViewModel.onBackButtonClicked(suggestedHandle, isCancelable)
            }
        }

    }
}
