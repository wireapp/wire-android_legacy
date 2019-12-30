package com.waz.zclient.core.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs

class EditTextDialogFragment : DialogFragment() {

    var listener: EditTextDialogFragmentListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val initialValue = arguments?.getString(DEFAULT_TEXT_BUNDLE_KEY, "")
        val inflater = requireActivity().layoutInflater
        val builder = AlertDialog.Builder(requireContext())
        val view = inflater.inflate(R.layout.dialog_fragment_edit_text, null)
        val editText: EditText = view.findViewById(R.id.edit_text) as EditText

        with(editText) {
            initialValue?.let {
                setText(it)
                setSelection(it.length)
            }
        }

        builder.setTitle(getString(R.string.pref_account_edit_name_title))
        builder.setView(view)
        builder.setPositiveButton(getString(android.R.string.ok)) { _, _ ->
            val newValue = editText.text.toString().trim()
            listener?.onTextEdited(newValue)
            dismiss()
        }
        builder.setNegativeButton(getString(android.R.string.cancel)) { _, _ -> dismiss() }


        return builder.create()
    }

    companion object {
        fun newInstance(defaultValue: String) = EditTextDialogFragment().withArgs { putString(DEFAULT_TEXT_BUNDLE_KEY, defaultValue) }
        private const val DEFAULT_TEXT_BUNDLE_KEY = "defaultTextBundleKey"
    }
}

