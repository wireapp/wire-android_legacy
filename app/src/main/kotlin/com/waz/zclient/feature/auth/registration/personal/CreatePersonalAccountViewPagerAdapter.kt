package com.waz.zclient.feature.auth.registration.personal

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountEmailFragment
import com.waz.zclient.feature.auth.registration.personal.phone.CreatePersonalAccountPhoneFragment

class CreatePersonalAccountViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = TAB_SIZE

    override fun createFragment(position: Int): Fragment {
        var fragment = Fragment()
        when (position) {
            EMAIL_TAB_POSITION -> fragment = CreatePersonalAccountEmailFragment.newInstance()
            PHONE_TAB_POSITION -> fragment = CreatePersonalAccountPhoneFragment.newInstance()
        }
        return fragment
    }

    companion object {
        private const val EMAIL_TAB_POSITION = 0
        private const val PHONE_TAB_POSITION = 1
        private const val TAB_SIZE = 2
    }
}
