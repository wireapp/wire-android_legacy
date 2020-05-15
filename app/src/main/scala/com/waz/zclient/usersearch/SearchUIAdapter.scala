/**
  * Wire
  * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.usersearch

import android.content.Context
import android.graphics.Rect
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.model._
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.views.{SingleUserRowView, TopUserChathead}
import com.waz.zclient.paintcode.{CreateGroupIcon, GuestIcon, ManageServicesIcon}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.SearchUIAdapter.Callback
import com.waz.zclient.usersearch.SearchUIAdapter.TopUsersViewHolder.TopUserAdapter
import com.waz.zclient.usersearch.listitems.{SearchViewItem, _}
import com.waz.zclient.usersearch.views.SearchResultConversationRowView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{ResColor, RichView, ViewUtils}

import scala.collection.mutable

class SearchUIAdapter(adapterCallback: Callback) extends RecyclerView.Adapter[RecyclerView.ViewHolder] {

  import SearchUIAdapter._
  import SearchViewItem._

  private val results = mutable.ArrayBuffer[SearchViewItem]()

  setHasStableIds(true)

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = {
    val view = LayoutInflater.from(parent.getContext).inflate(viewType match {
      case TopUsers          => R.layout.startui_top_users
      case ConnectedUser |
           UnconnectedUser |
           Integration       => R.layout.single_user_row
      case GroupConversation => R.layout.startui_conversation
      case SectionHeader     => R.layout.startui_section_header
      case Expand            => R.layout.startui_section_expander
      case NewConversation |
           NewGuestRoom |
           ManageServices    => R.layout.startui_button
      case _                 => -1
    }, parent, false)

    viewType match {
      case TopUsers          => new TopUsersViewHolder(view, new TopUserAdapter(adapterCallback), parent.getContext)
      case ConnectedUser |
           UnconnectedUser   => new UserViewHolder(view.asInstanceOf[SingleUserRowView], adapterCallback)
      case GroupConversation => new ConversationViewHolder(view, adapterCallback)
      case SectionHeader     => new SectionHeaderViewHolder(view)
      case Expand            => new SectionExpanderViewHolder(view)
      case Integration       => new IntegrationViewHolder(view.asInstanceOf[SingleUserRowView], adapterCallback)
      case NewConversation   => new CreateConversationButtonViewHolder(view, adapterCallback)
      case NewGuestRoom      => new NewGuestRoomViewHolder(view, adapterCallback)
      case ManageServices    => new ManageServicesViewHolder(view, adapterCallback)
      case _                 => null
    }
  }

  override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int): Unit = {
    val item: SearchViewItem = results(position)
    item.itemType match {
      case TopUsers =>
        holder.asInstanceOf[TopUsersViewHolder].bind(item.asInstanceOf[TopUserViewItem])
      case GroupConversation =>
        holder.asInstanceOf[ConversationViewHolder].bind(item.asInstanceOf[GroupConversationViewItem])
      case ConnectedUser | UnconnectedUser =>
        holder.asInstanceOf[UserViewHolder].bind(item.asInstanceOf[ConnectionViewItem])
      case SectionHeader =>
        holder.asInstanceOf[SectionHeaderViewHolder].bind(item.asInstanceOf[SectionViewItem])
      case Expand =>
        holder.asInstanceOf[SectionExpanderViewHolder].bind(item.asInstanceOf[ExpandViewItem], new View.OnClickListener {
          override def onClick(view: View): Unit = {
            if (item.section == SectionViewItem.ContactsSection) {
              adapterCallback.onContactsExpanded()
            } else {
              adapterCallback.onGroupsExpanded()
            }
          }
        })
      case Integration =>
        holder.asInstanceOf[IntegrationViewHolder].bind(item.asInstanceOf[IntegrationViewItem])
      case _ =>
    }
  }

  def updateResults(results: List[SearchViewItem]): Unit = {
    this.results.clear()
    this.results ++= results
    notifyDataSetChanged()
  }

  override def getItemCount: Int = results.size

  override def getItemViewType(position: Int): Int =
    results.lift(position).fold(-1)(_.itemType)

  override def getItemId(position: Int): Long =
    results.lift(position).fold(-1L)(_.id)
}

object SearchUIAdapter {

  import SectionViewItem._

  trait Callback {

    def onContactsExpanded(): Unit

    def onGroupsExpanded(): Unit

    def onUserClicked(user: UserData): Unit

    def onIntegrationClicked(data: IntegrationData): Unit

    def onCreateConversationClicked(): Unit

    def onCreateGuestRoomClicked(): Unit

    def onConversationClicked(conversation: ConversationData): Unit

    def onManageServicesClicked(): Unit
  }

  class CreateConversationButtonViewHolder(view: View, callback: SearchUIAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx: Context = view.getContext

    private val iconView = view.findViewById[View](R.id.icon)

    iconView.setBackground(returning(CreateGroupIcon(R.color.white))(_.setPadding(new Rect(iconView.getPaddingLeft, iconView.getPaddingTop, iconView.getPaddingRight, iconView.getPaddingBottom))))
    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.create_group_conversation)
    view.onClick(callback.onCreateConversationClicked())
    view.setId(R.id.create_group_button)
  }

  class NewGuestRoomViewHolder(view: View, callback: SearchUIAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx: Context = view.getContext

    private val iconView = view.findViewById[View](R.id.icon)

    iconView.setBackground(returning(GuestIcon(R.color.white))(_.setPadding(new Rect(iconView.getPaddingLeft, iconView.getPaddingTop, iconView.getPaddingRight, iconView.getPaddingBottom))))
    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.create_guest_room_conversation)
    view.onClick(callback.onCreateGuestRoomClicked())
    view.setId(R.id.create_guest_room_button)
  }

  class ManageServicesViewHolder(view: View, callback: SearchUIAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx: Context = view.getContext

    private val iconView = view.findViewById[View](R.id.icon)

    iconView.setBackground(returning(ManageServicesIcon(ResColor.fromId(R.color.white))) {
      _.setPadding(new Rect(iconView.getPaddingLeft, iconView.getPaddingTop, iconView.getPaddingRight, iconView.getPaddingBottom))
    })
    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.manage_services)
    view.onClick(callback.onManageServicesClicked())
    view.setId(R.id.manage_services_button)
  }

  class TopUsersViewHolder(view: View, topUserAdapter: TopUserAdapter, context: Context) extends RecyclerView.ViewHolder(view) {
    val topUsersRecyclerView: RecyclerView = ViewUtils.getView[RecyclerView](view, R.id.rv_top_users)
    val layoutManager                      = new LinearLayoutManager(context)

    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL)
    topUsersRecyclerView.setLayoutManager(layoutManager)
    topUsersRecyclerView.setHasFixedSize(false)
    topUsersRecyclerView.setAdapter(this.topUserAdapter)

    def bind(item: TopUserViewItem): Unit = topUserAdapter.setTopUsers(item.topUsers)
  }

  object TopUsersViewHolder {

    class TopUserAdapter(callback: Callback) extends RecyclerView.Adapter[TopUserViewHolder] {
      private var topUsers = Seq[UserData]()

      def onCreateViewHolder(parent: ViewGroup, viewType: Int): TopUserViewHolder =
        new TopUserViewHolder(LayoutInflater.from(parent.getContext).inflate(R.layout.startui_top_user, parent, false).asInstanceOf[TopUserChathead], callback)

      def onBindViewHolder(holder: TopUserViewHolder, position: Int): Unit = holder.bind(topUsers(position))

      def getItemCount: Int = topUsers.length

      def setTopUsers(topUsers: Seq[UserData]): Unit = {
        this.topUsers = topUsers
        notifyDataSetChanged()
      }

      def reset(): Unit = topUsers = Seq()
    }

    class TopUserViewHolder(view: TopUserChathead, callback: Callback) extends RecyclerView.ViewHolder(view) {
      private var user = Option.empty[UserData]

      view.onClick(user.foreach(callback.onUserClicked))

      def bind(user: UserData): Unit = {
        this.user = Some(user)
        view.setUser(user)
      }
    }

  }

  class UserViewHolder(view: SingleUserRowView, callback: Callback) extends RecyclerView.ViewHolder(view) {
    private var userData = Option.empty[UserData]

    view.onClick(userData.foreach(callback.onUserClicked))
    view.showArrow(false)
    view.showCheckbox(false)
    view.setTheme(Theme.Dark, background = false)

    def bind(item: ConnectionViewItem): Unit = {
      this.userData = Some(item.user)
      view.setUserData(item.user, item.selfTeamId)
    }
  }

  class ConversationViewHolder(view: View, callback: Callback) extends RecyclerView.ViewHolder(view) {
    private val conversationRowView = ViewUtils.getView[SearchResultConversationRowView](view, R.id.srcrv_startui_conversation)
    private var conversation        = Option.empty[ConversationData]

    view.onClick(conversation.foreach(callback.onConversationClicked))
    conversationRowView.applyDarkTheme()

    def bind(item: GroupConversationViewItem): Unit = {
      conversation = Some(item.conversation)
      conversationRowView.setConversation(item.conversation)
    }
  }

  class IntegrationViewHolder(view: SingleUserRowView, callback: Callback) extends RecyclerView.ViewHolder(view) {
    private var integrationData: Option[IntegrationData] = None

    view.onClick(integrationData.foreach(i => callback.onIntegrationClicked(i)))
    view.showArrow(false)
    view.showCheckbox(false)
    view.setTheme(Theme.Dark, background = false)
    view.setSeparatorVisible(true)

    def bind(item: IntegrationViewItem): Unit = {
      this.integrationData = Some(item.integration)
      view.setIntegration(item.integration)
    }
  }

  class SectionExpanderViewHolder(val view: View) extends RecyclerView.ViewHolder(view) {
    private val viewAllTextView = ViewUtils.getView[TypefaceTextView](view, R.id.ttv_startui_section_header)

    def bind(item: ExpandViewItem, clickListener: View.OnClickListener): Unit = {
      val title = getString(R.string.people_picker__search_result__expander_title, Integer.toString(item.itemCount))(view.getContext)
      viewAllTextView.setText(title)
      viewAllTextView.setOnClickListener(clickListener)
    }
  }

  class SectionHeaderViewHolder(val view: View) extends RecyclerView.ViewHolder(view) {
    private val sectionHeaderView: TextView = ViewUtils.getView(view, R.id.ttv_startui_section_header)

    private implicit val context: Context = sectionHeaderView.getContext

    def bind(item: SectionViewItem): Unit = sectionHeaderView.setText(
      item.section match {
        case TopUsersSection                                => getString(R.string.people_picker__top_users_header_title)
        case GroupConversationsSection if item.name.isEmpty => getString(R.string.people_picker__search_result_conversations_header_title)
        case GroupConversationsSection                      => getString(R.string.people_picker__search_result_team_conversations_header_title, item.name)
        case ContactsSection                                => getString(R.string.people_picker__search_result_connections_searched_header_title)
        case DirectorySection                               => getString(R.string.people_picker__search_result_others_header_title)
        case IntegrationsSection                            => getString(R.string.integrations_picker__section_title)
      }
    )
  }
}


