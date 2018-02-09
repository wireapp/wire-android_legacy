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
package com.waz.zclient.usersearch.views

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatCheckBox
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.{CompoundButton, FrameLayout}
import com.waz.model.{UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal, SourceStream}
import com.waz.utils.returning
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.{R, ViewHelper}

class SearchResultUserRowView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with UserRowView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  inflate(R.layout.list_row_pickuser_searchuser, this)

  private val chathead = findById[ChatheadView](R.id.cv_pickuser__searchuser_chathead)
  private val contactListItemTextView = findById[ContactListItemTextView](R.id.clitv__contactlist__user__text_view)
  private val guestLabel = findById[TypefaceTextView](R.id.guest_indicator)
  private var showContactInfo: Boolean = false
  private var showCheckbox: Boolean = false
  private val userId = Signal[UserId]()
  private val checkbox = findById[AppCompatCheckBox](R.id.pick_user_checkbox)
  returning(ContextCompat.getDrawable(getContext, R.drawable.checkbox_black)){ btn =>
    btn.setLevel(1)
    checkbox.setButtonDrawable(btn)
  }
  checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit =
      onSelectionChanged ! isChecked
  })
  setClickable(true)
  setFocusable(true)
  setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = checkbox.toggle()
  })

  private val isGuest = for{
    z <- inject[Signal[ZMessaging]]
    uId <- userId
    isGuest <- z.teams.isGuest(uId)
    knownUsers <- z.users.acceptedOrBlockedUsers
  } yield isGuest && knownUsers.contains(uId)

  private var userData = Option.empty[UserData]

  val onSelectionChanged: SourceStream[Boolean] = EventStream()

  def setUser(userData: UserData): Unit = {
    this.userData = Some(userData)
    userId ! userData.id
    contactListItemTextView.setUser(userData, showContactInfo)
    chathead.setUserId(userData.id)
  }

  def setShowContactInfo(showContactInfo: Boolean): Unit = {
    this.showContactInfo = showContactInfo
  }

  def getUser = userData.map(_.id)

  def onClicked(): Unit = {
    setSelected(!isSelected && showCheckbox)
  }

  def setChecked(checked: Boolean): Unit ={
    checkbox.setChecked(checked)
  }

  def applyDarkTheme(): Unit = {
    contactListItemTextView.applyDarkTheme()
    returning(ContextCompat.getDrawable(getContext, R.drawable.checkbox)){ btn =>
      btn.setLevel(1)
      checkbox.setButtonDrawable(btn)
    }
  }

  def setIsAddingPeople(adding: Boolean): Unit = {
    showCheckbox = adding
    checkbox.setVisibility(if (adding) View.VISIBLE else View.GONE)
    setBackground(if (adding) null else ContextUtils.getDrawable(R.drawable.selector__transparent_button)(context))
  }

  isGuest.on(Threading.Ui) { guest =>
    guestLabel.setVisibility(if (guest) View.VISIBLE else View.GONE)
  }
}
