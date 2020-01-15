package com.waz.zclient.settings.account.edithandle

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleFragment : DialogFragment() {

    companion object {
        private const val CURRENT_HANDLE_BUNDLE_KEY = "currentHandleBundleKey"

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
        return view
    }

    private fun initViews(view: View?) {
        view?.let {
            handleInput = it.findViewById(R.id.edit_handle_edit_text)
            handleInput.addTextChangedListener(changeHandleTextWatcher)

            it.findViewById<View>(R.id.edit_handle_ok_button).setOnClickListener {
                editHandleFragmentViewModel.onOkButtonClicked(handleInput.text.toString())
            }

            it.findViewById<View>(R.id.edit_handle_back_button).setOnClickListener {
                editHandleFragmentViewModel.onBackButtonClicked()
            }
        }

    }
}
