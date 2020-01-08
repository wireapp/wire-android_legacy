package com.waz.zclient.core.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs
import kotlinx.android.synthetic.main.dialog_fragment_edit_text.view.*


class EditTextDialogFragment : DialogFragment() {

    private var listener: EditTextDialogFragmentListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val initialValue = arguments?.getString(DEFAULT_TEXT_BUNDLE_KEY, "")
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_fragment_edit_text, null)

        with(view.edit_text) {
            initialValue?.let {
                setText(it)
                setSelection(it.length)
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pref_account_edit_name_title))
            .setView(view).setPositiveButton(getString(android.R.string.ok)) { _, _ -> positiveButtonAction(view.edit_text.text.toString().trim()) }
            .setNegativeButton(getString(android.R.string.cancel)) { _, _ -> negativeButtonAction() }
            .create()
    }


    private fun positiveButtonAction(newValue : String) {
        listener?.onTextEdited(newValue)
        dismiss()
    }

    private fun negativeButtonAction() {
        dismiss()
    }

    companion object {
        fun newInstance(defaultValue: String, dialogListener: EditTextDialogFragmentListener):
            EditTextDialogFragment = EditTextDialogFragment().withArgs {
            putString(
                DEFAULT_TEXT_BUNDLE_KEY,
                defaultValue
            )
        }.also { it.listener = dialogListener }

        private const val DEFAULT_TEXT_BUNDLE_KEY = "defaultTextBundleKey"
    }
}

