package com.waz.zclient.settings.account.edithandle

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.core.ui.text.TextChangedListener
import kotlinx.android.synthetic.main.fragment_edit_handle_dialog.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleFragment : DialogFragment() {

    private val editHandleViewModel: EditHandleViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dark_Preferences)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_edit_handle_dialog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListeners()
    }

    private fun initListeners() {
        edit_handle_edit_text.addTextChangedListener(object : TextChangedListener {
            override fun afterTextChanged(s: Editable?) {
                editHandleViewModel.afterHandleTextChanged(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                editHandleViewModel.beforeHandleTextChanged(s.toString())
            }
        })

        edit_handle_ok_button.setOnClickListener {
            editHandleViewModel.onOkButtonClicked(edit_handle_edit_text.text.toString())
        }

        edit_handle_back_button.setOnClickListener {
            editHandleViewModel.onBackButtonClicked()
        }
    }

    companion object {
        private const val CURRENT_HANDLE_BUNDLE_KEY = "currentHandleBundleKey"

        fun newInstance(currentHandle: String?):
            EditHandleFragment = EditHandleFragment()
            .withArgs {
                putString(CURRENT_HANDLE_BUNDLE_KEY, currentHandle)
            }
    }
}
