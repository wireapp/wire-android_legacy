package com.waz.zclient.appentry

import androidx.fragment.app.{Fragment, FragmentActivity, FragmentTransaction}
import com.waz.zclient.R

object TransactionHandler {
  def showFragment(activity: FragmentActivity, f: => Fragment, tag: String, animated: Boolean = true, layoutId: Int): Unit = {
    val transaction = activity.getSupportFragmentManager.beginTransaction()
    if (animated) setDefaultAnimation(transaction)
    transaction
      .replace(layoutId, f, tag)
      .addToBackStack(tag)
      .commit
  }

  private def setDefaultAnimation(transaction: FragmentTransaction): FragmentTransaction = {
    transaction.setCustomAnimations(
      R.anim.fragment_animation_second_page_slide_in_from_right,
      R.anim.fragment_animation_second_page_slide_out_to_left,
      R.anim.fragment_animation_second_page_slide_in_from_left,
      R.anim.fragment_animation_second_page_slide_out_to_right)
    transaction
  }
}
