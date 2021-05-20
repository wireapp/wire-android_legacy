package com.waz.zclient.legalhold

import android.content.{Context, Intent}
import android.os.Bundle
import com.waz.zclient.{BaseActivity, R}
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.utils.RichView

class SelfUserLegalHoldInfoActivity extends BaseActivity {

  private lazy val closeButton = findById[GlyphButton](R.id.legal_hold_info_close_button)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_legal_hold_info)
    setUpCloseButton()
    showLegalHoldInfo()
  }

  private def setUpCloseButton(): Unit = closeButton.onClick { finish() }

  private def showLegalHoldInfo(): Unit =
    getSupportFragmentManager.beginTransaction()
      .replace(
        R.id.legal_hold_info_fragment_container_layout,
        LegalHoldInfoFragment.newInstance(None)
      ).commit()

  override def finish(): Unit = {
    super.finish()
    overridePendingTransition(R.anim.fade_in, R.anim.out_to_bottom_pop_exit)
  }
}

object SelfUserLegalHoldInfoActivity {
  def newIntent(context: Context) = new Intent(context, classOf[SelfUserLegalHoldInfoActivity])
}
