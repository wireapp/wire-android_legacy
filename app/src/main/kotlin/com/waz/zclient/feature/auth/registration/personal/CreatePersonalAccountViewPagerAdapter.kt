package com.waz.zclient.feature.auth.registration.personal

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountEmailFragment
import com.waz.zclient.feature.auth.registration.personal.phone.CreatePersonalAccountPhoneFragment

class CreatePersonalAccountViewPagerAdapter(fragmentManager: FragmentManager, private val titles: List<String>) :
    FragmentPagerAdapter(fragmentManager) {

    override fun getCount(): Int = titles.size

    override fun getItem(position: Int): Fragment {
        var fragment = Fragment()
        when (position) {
            EMAIL_TAB_POSITION -> fragment = CreatePersonalAccountEmailFragment.newInstance()
            PHONE_TAB_POSITION -> fragment = CreatePersonalAccountPhoneFragment.newInstance()
        }
        return fragment
    }

    override fun getPageTitle(position: Int): CharSequence = titles[position].toUpperCase()

    companion object {
        private const val EMAIL_TAB_POSITION = 0
        private const val PHONE_TAB_POSITION = 1
    }
}
