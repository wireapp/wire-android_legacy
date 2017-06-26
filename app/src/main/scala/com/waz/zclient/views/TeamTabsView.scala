/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.views

import android.content.Context
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.{View, ViewGroup}
import com.waz.ZLog
import com.waz.ZLog._
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.controllers.UserAccountsController
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector, ViewHelper}

class TeamTabsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends RecyclerView(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  val onTabClick = EventStream[Either[UserData, TeamData]]()

  setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false){
    override def canScrollHorizontally = true
    override def canScrollVertically = false
  })
  setOverScrollMode(View.OVER_SCROLL_NEVER)
  private val adapter = new TeamTabsAdapter(context)
  setAdapter(adapter)
}

class TeamTabViewHolder(view: TeamTabButton) extends RecyclerView.ViewHolder(view){

  def bind(accountData: AccountData, userData: UserData, selected: Boolean, unreadCount: Int): Unit = {
    view.setUserData(accountData, userData, selected, unreadCount)
  }
}

class TeamTabsAdapter(context: Context)(implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[TeamTabViewHolder] with Injectable {
  private implicit val tag: LogTag = logTagFor[TeamTabsAdapter]

  val controller = inject[UserAccountsController]
  implicit val uiStorage = inject[UiStorage]

  val onItemClick = EventStream[AccountData]()

  onItemClick{ account =>
    ZMessaging.currentAccounts.switchAccount(account.id)
  }

  val usersSignal = for {
    accounts <- controller.accounts
    users <- Signal.sequence(accounts.flatMap(_.userId).map(UserSignal(_)):_*)
  } yield accounts.zip(users).sortBy(_._1.id.str)

  private var users = Seq[(AccountData, UserData, Int)]()

  usersSignal.on(Threading.Ui){ users =>
      this.users = users.map(u => (u._1, u._2, 0))
      notifyDataSetChanged()
  }

  override def getItemCount = users.size

  override def onBindViewHolder(holder: TeamTabViewHolder, position: Int) = {
    getItem(position) match {
      case Some((accountData, userData, messageCount)) =>

        val selected = ZMessaging.currentAccounts.currentAccountData.currentValue match {
          case Some(Some(currentAccount)) => currentAccount.id == accountData.id
          case _ => false
        }
        holder.bind(accountData, userData, selected, messageCount)
      case _ =>
        ZLog.error("Invalid get item index")
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int) = {
    val view = new TeamTabButton(context)
    view.setOnClickListener(new OnClickListener {
      override def onClick(v: View) = {
        Option(v.asInstanceOf[TeamTabButton]).flatMap(_.accountData).foreach(onItemClick ! _)
      }
    })
    new TeamTabViewHolder(view)
  }

  def getItem(position: Int): Option[(AccountData, UserData, Int)] = {
    users.lift(position)
  }
}
