package com.waz.zclient.settings.account.color

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs
import kotlinx.android.synthetic.main.dialog_fragment_accent_color.view.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
class AccentColorDialogFragment : DialogFragment() {

    private var listener: OnAccentColorChangedListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_fragment_accent_color, null)

        val initialColorId = arguments?.getInt(DEFAULT_ACCENT_COLOR_BUNDLE_KEY, DEFAULT_ACCENT_COLOR_ID)

        val colors = resources.getIntArray(R.array.settings_accent_colors_ids).zip(resources.getIntArray(R.array.settings_accent_colors))
            .map { AccentColor(it.first, it.second) }

        initialColorId?.let {
            view.accent_color_recycler_view.adapter =
                AccentColorAdapter(colors, it, listener)
        }

        return AlertDialog.Builder(requireContext()).setView(view).setCancelable(true).create()
    }

    companion object {
        private const val DEFAULT_ACCENT_COLOR_BUNDLE_KEY = "defaultAccentColorBundleKey"
        private const val DEFAULT_ACCENT_COLOR_ID = 1

        fun newInstance(defaultColorId: Int,onAccentColorChangedListener: OnAccentColorChangedListener): AccentColorDialogFragment = AccentColorDialogFragment()
            .withArgs { putInt(DEFAULT_ACCENT_COLOR_BUNDLE_KEY, defaultColorId) }
            .also {
                it.listener = onAccentColorChangedListener
            }
    }
}
