package com.waz.zclient.settings.account.editphonenumber

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.recyclerview.widget.DividerItemDecoration
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.getDeviceLocale
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.user.domain.usecase.phonenumber.Country
import kotlinx.android.synthetic.main.fragment_dialog_country_code_picker.*
import org.koin.android.viewmodel.ext.android.viewModel


class CountryCodePickerFragment : DialogFragment() {

    private val countryCodePickerViewModel: CountryCodePickerViewModel by viewModel()

    private val countryDisplayName: String by lazy {
        arguments?.getString(COUNTRY_DISPLAY_NAME_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    private val countryAdapter: CountryCodesRecyclerAdapter by lazy {
        CountryCodesRecyclerAdapter {
            countryCodePickerViewModel.onCountryCodeChanged(it, countryDisplayName)
        }
    }

    private var countryCodeListener: CountryCodePickerListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_dialog_country_code_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initCountriesList()
        initDismiss()
        lifecycleScope.launchWhenResumed {
            countryCodePickerViewModel.loadCountries(requireActivity().getDeviceLocale().language)
        }
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dark_Preferences)
    }

    private fun initDismiss() {
        countryCodePickerViewModel.dismissLiveData.observe(viewLifecycleOwner) {
            dismiss()
        }
    }

    private fun initCountriesList() {
        countryCodePickerDialogRecyclerView.adapter = countryAdapter
        val divider = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        countryCodePickerDialogRecyclerView.addItemDecoration(divider)
        countryCodePickerViewModel.countriesLiveData.observe(viewLifecycleOwner) {
            countryAdapter.updateList(it)
        }
        countryCodePickerViewModel.selectedCountryLiveData.observe(viewLifecycleOwner) {
            countryCodeListener?.onCountryCodeSelected(it)
        }
    }

    interface CountryCodePickerListener {
        fun onCountryCodeSelected(countryCode: Country)
    }

    companion object {

        private const val COUNTRY_DISPLAY_NAME_BUNDLE_KEY = "countryCodeBundleKey"

        fun newInstance(countryDisplayName: String, listener: CountryCodePickerListener) =
            CountryCodePickerFragment()
                .withArgs { putString(COUNTRY_DISPLAY_NAME_BUNDLE_KEY, countryDisplayName) }
                .also { it.countryCodeListener = listener }
    }
}
