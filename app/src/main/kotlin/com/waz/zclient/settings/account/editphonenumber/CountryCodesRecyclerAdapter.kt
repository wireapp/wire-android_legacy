package com.waz.zclient.settings.account.editphonenumber

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.user.domain.usecase.phonenumber.Country
import kotlinx.android.synthetic.main.item_view_country_code.view.*

class CountryCodesRecyclerAdapter : RecyclerView.Adapter<CountryCodesRecyclerAdapter.CountryCodeViewHolder>() {

    private var countries: List<Country> = listOf()

    private var listener: CountryCodeRecyclerItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryCodeViewHolder =
        CountryCodeViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_view_country_code, parent, false))

    override fun getItemCount(): Int = countries.size

    override fun onBindViewHolder(holder: CountryCodeViewHolder, position: Int) {
        holder.bind(countries[position])
    }

    fun setOnItemClickListener(listener: CountryCodeRecyclerItemClickListener) {
        this.listener = listener
    }

    fun updateList(newCountries: List<Country>) {
        countries = newCountries
        notifyDataSetChanged()
    }

    inner class CountryCodeViewHolder(val root: View) : RecyclerView.ViewHolder(root) {

        fun bind(country: Country) {
            with(itemView) {
                itemViewCountryCodeDisplayName.text = country.countryDisplayName
                itemViewCountryCodeCode.text = country.countryCode
                setOnClickListener {
                    listener?.onCountryCodeClicked(country)
                }
            }
        }
    }

    interface CountryCodeRecyclerItemClickListener {
        fun onCountryCodeClicked(country: Country)
    }
}
