/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.View
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, verbose}
import com.waz.utils.returning
import com.waz.zclient.Intents._
import com.waz.zclient.quickreply.QuickReplyFragment
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.{ContextUtils, ViewUtils}

class PopupActivity extends BaseActivity with ActivityHelper { self =>

  implicit lazy val context = this

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    verbose(s"onCreate: ${getIntent.log}")
    ViewUtils.unlockOrientation(this)
    setContentView(R.layout.popup_reply)
    returning(findById[Toolbar](R.id.toolbar)) { toolbar =>
      setSupportActionBar(toolbar)
      setTitle("")
      toolbar.setNavigationIcon(ContextUtils.getDrawable(R.drawable.action_back_light, Some(getTheme)))
      toolbar.setNavigationOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = {
          KeyboardUtils.closeKeyboardIfShown(self)
          onBackPressed()
        }
      })
    }

    Option(getIntent) match {
      case Some(i) => showQuickReplyFragment(i)
      case None    => finish()
    }
  }

  override protected def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
    showQuickReplyFragment(intent)
  }

  private def showQuickReplyFragment(intent: Intent) = {

    (intent.accountId, intent.convId) match {
      case (Some(acc), Some(conv)) =>
        getSupportFragmentManager.beginTransaction.replace(R.id.fl__quick_reply__container, QuickReplyFragment.newInstance(acc, conv)).commit
      case _ => error("Unknown account or conversation id - can't show QuickReplyFragment")
    }
  }

  override def getBaseTheme: Int = R.style.Theme_Popup
}
