package com.waz.zclient.settings.account.editphonenumber

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.getDeviceLocale
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.user.domain.usecase.phonenumber.Country
import kotlinx.android.synthetic.main.fragment_dialog_country_code_picker.view.*
import org.koin.android.viewmodel.ext.android.viewModel

class CountryCodePickerFragment : DialogFragment() {

    private val countryCodePickerViewModel: CountryCodePickerViewModel by viewModel()

    private lateinit var rootView: View

    private val countryDisplayName: String by lazy {
        arguments?.getString(COUNTRY_DISPLAY_NAME_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    private val countryAdapter: CountryCodesRecyclerAdapter by lazy {
        CountryCodesRecyclerAdapter()
    }

    private var countryCodeListener: CountryCodePickerListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_dialog_country_code_picker, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()

        lifecycleScope.launchWhenResumed {
            countryCodePickerViewModel.loadCountries(requireActivity().getDeviceLocale().language)
        }
    }

    private fun initRecyclerView() {
        rootView.countryCodePickDialogRecyclerView.adapter = countryAdapter
        countryAdapter.setOnItemClickListener(object : CountryCodesRecyclerAdapter.CountryCodeRecyclerItemClickListener {
            override fun onCountryCodeClicked(country: Country) {
                countryCodePickerViewModel.onCountryCodeChanged(country, countryDisplayName)
            }
        })
        countryCodePickerViewModel.countriesLiveData.observe(viewLifecycleOwner) {
            countryAdapter.updateList(it as MutableList<Country>)
        }

        countryCodePickerViewModel.countryLiveData.observe(viewLifecycleOwner) {
            when (it) {
                Country.EMPTY -> dismiss()
                else -> {
                    countryCodeListener?.onCountryCodeSelected(it)
                    dismiss()
                }
            }
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
