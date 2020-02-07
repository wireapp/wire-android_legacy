package com.waz.zclient.core.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.withArgs
import kotlinx.android.synthetic.main.dialog_fragment_edit_text.view.*

class EditTextDialogFragment : DialogFragment() {

    private var listener: EditTextDialogFragmentListener? = null

    interface EditTextDialogFragmentListener {
        fun onTextEdited(newValue: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val title = arguments?.getString(TITLE_BUNDLE_KEY, String.empty())
        val initialValue = arguments?.getString(DEFAULT_TEXT_BUNDLE_KEY, String.empty())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_fragment_edit_text, null)

        with(view.editTextDialogInputEditText) {
            initialValue?.let {
                setText(it)
                setSelection(it.length)
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                positiveButtonAction(view.editTextDialogInputEditText.text.toString().trim())
            }
            .setNegativeButton(getString(android.R.string.cancel)) { _, _ ->
                negativeButtonAction()
            }
            .create()
    }

    private fun positiveButtonAction(newValue: String) {
        listener?.onTextEdited(newValue)
        dismiss()
    }

    private fun negativeButtonAction() {
        dismiss()
    }

    companion object {
        private const val TITLE_BUNDLE_KEY = "titleBundleKey"
        private const val DEFAULT_TEXT_BUNDLE_KEY = "defaultTextBundleKey"

        fun newInstance(title: String, defaultValue: String, dialogListener: EditTextDialogFragmentListener):
            EditTextDialogFragment = EditTextDialogFragment()
            .withArgs {
                putString(TITLE_BUNDLE_KEY, title)
                putString(DEFAULT_TEXT_BUNDLE_KEY, defaultValue)
            }.also { it.listener = dialogListener }
    }
}
