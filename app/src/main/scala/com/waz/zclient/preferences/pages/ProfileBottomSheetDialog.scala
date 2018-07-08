/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.waz.zclient.preferences.pages

import android.content.{Context, Intent}
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.view.View
import android.widget.LinearLayout
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.utils.RichView
import com.waz.zclient.{DialogHelper, R}

class ProfileBottomSheetDialog(val context: Context, theme: Int) extends BottomSheetDialog(context, theme) with DialogHelper {

  val MaxAccountsCount = 2

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    val view = getLayoutInflater.inflate(R.layout.profile__bottom__menu, null).asInstanceOf[LinearLayout]
    setContentView(view)

    val createTeamButton = findViewById(R.id.profile_menu_create).asInstanceOf[View]
    val addAccountButton = findViewById(R.id.profile_menu_add).asInstanceOf[View]

    createTeamButton.onClick {
      createTeamButton.setEnabled(false)
      val intent = new Intent(getContext, classOf[AppEntryActivity])
      intent.putExtras(AppEntryActivity.getCreateTeamArgs)
      getContext.startActivity(intent)
      dismiss()
    }

    addAccountButton.onClick {
      addAccountButton.setEnabled(false)
      val intent = new Intent(getContext, classOf[AppEntryActivity])
      intent.putExtras(AppEntryActivity.getLoginArgs)
      getContext.startActivity(intent)
      dismiss()
    }

    ZMessaging.currentAccounts.accountManagers.map(_.size).on(Threading.Ui) { count =>
      addAccountButton.setAlpha(if (count < MaxAccountsCount) 1f else 0.5f)
      addAccountButton.setEnabled(count < MaxAccountsCount)
    }
  }

}
