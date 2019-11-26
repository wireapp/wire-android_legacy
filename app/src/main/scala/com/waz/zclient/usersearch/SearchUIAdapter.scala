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

  private var results = mutable.ListBuffer[SearchViewItem]()

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
        val topUserData = item.asInstanceOf[TopUserViewItem].data
        holder.asInstanceOf[TopUsersViewHolder].bind(topUserData)
      case GroupConversation =>
        val groupConversationData = item.asInstanceOf[GroupConversationViewItem].data
        holder.asInstanceOf[ConversationViewHolder].bind(groupConversationData)
      case ConnectedUser | UnconnectedUser =>
        val userConnectionData = item.asInstanceOf[ConnectionViewItem].data
        holder.asInstanceOf[UserViewHolder].bind(userConnectionData)
      case SectionHeader =>
        val sectionData = item.asInstanceOf[SectionViewItem].data
        holder.asInstanceOf[SectionHeaderViewHolder].bind(sectionData)
      case Expand =>
        val expandData = item.asInstanceOf[ExpandViewItem].data
        holder.asInstanceOf[SectionExpanderViewHolder].bind(expandData, new View.OnClickListener {
          override def onClick(view: View): Unit = {
            if (item.section == SectionViewItem.ContactsSection) {
              adapterCallback.onContactsExpanded()
            } else {
              adapterCallback.onGroupsExpanded()
            }
          }
        })
      case Integration =>
        val integrationData = item.asInstanceOf[IntegrationViewItem].data
        holder.asInstanceOf[IntegrationViewHolder].bind(integrationData)
      case _ =>
    }
  }

  def updateResults(results: mutable.ListBuffer[SearchViewItem]): Unit = {
    this.results = results
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

    def onUserClicked(userId: UserId): Unit

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

    def bind(topUserViewModel: TopUserViewModel): Unit = topUserAdapter.setTopUsers(topUserViewModel.topUsers)
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

      view.onClick(user.map(_.id).foreach(callback.onUserClicked))

      def bind(user: UserData): Unit = {
        this.user = Some(user)
        view.setUser(user)
      }
    }

  }

  class UserViewHolder(view: SingleUserRowView, callback: Callback) extends RecyclerView.ViewHolder(view) {
    private var userData = Option.empty[UserData]

    view.onClick(userData.map(_.id).foreach(callback.onUserClicked))
    view.showArrow(false)
    view.showCheckbox(false)
    view.setTheme(Theme.Dark, background = false)

    def bind(connectionViewModel: ConnectionViewModel): Unit = {
      val userData = connectionViewModel.results(connectionViewModel.indexVal)
      this.userData = Some(userData)
      val teamId = connectionViewModel.team.map(_.id)
      view.setUserData(userData, teamId, connectionViewModel.shouldHideUserStatus)
    }
  }

  class ConversationViewHolder(view: View, callback: Callback) extends RecyclerView.ViewHolder(view) {
    private val conversationRowView = ViewUtils.getView[SearchResultConversationRowView](view, R.id.srcrv_startui_conversation)
    private var conversation        = Option.empty[ConversationData]

    view.onClick(conversation.foreach(callback.onConversationClicked))
    conversationRowView.applyDarkTheme()

    def bind(groupConversationViewModel: GroupConversationViewModel): Unit = {
      val conversationData = groupConversationViewModel.conversations(groupConversationViewModel.indexVal)
      conversation = Some(conversationData)
      conversationRowView.setConversation(conversationData)
    }
  }

  class IntegrationViewHolder(view: SingleUserRowView, callback: Callback) extends RecyclerView.ViewHolder(view) {
    private var integrationData: Option[IntegrationData] = None

    view.onClick(integrationData.foreach(i => callback.onIntegrationClicked(i)))
    view.showArrow(false)
    view.showCheckbox(false)
    view.setTheme(Theme.Dark, background = false)
    view.setSeparatorVisible(true)

    def bind(integrationViewModel: IntegrationViewModel): Unit = {
      val integrationData = integrationViewModel.integrations(integrationViewModel.indexVal)
      this.integrationData = Some(integrationData)
      view.setIntegration(integrationData)
    }
  }

  class SectionExpanderViewHolder(val view: View) extends RecyclerView.ViewHolder(view) {
    private val viewAllTextView = ViewUtils.getView[TypefaceTextView](view, R.id.ttv_startui_section_header)

    def bind(expandViewModel: ExpandViewModel, clickListener: View.OnClickListener): Unit = {
      val itemCount = expandViewModel.itemCount
      val title = getString(R.string.people_picker__search_result__expander_title, Integer.toString(itemCount))(view.getContext)
      viewAllTextView.setText(title)
      viewAllTextView.setOnClickListener(clickListener)
    }
  }

  class SectionHeaderViewHolder(val view: View) extends RecyclerView.ViewHolder(view) {
    private val sectionHeaderView: TextView = ViewUtils.getView(view, R.id.ttv_startui_section_header)

    private implicit val context: Context = sectionHeaderView.getContext

    def bind(sectionViewModel: SectionViewModel): Unit = {
      val section = sectionViewModel.section
      val teamName = sectionViewModel.name
      val title = section match {
        case TopUsersSection                               => getString(R.string.people_picker__top_users_header_title)
        case GroupConversationsSection if teamName.isEmpty => getString(R.string.people_picker__search_result_conversations_header_title)
        case GroupConversationsSection                     => getString(R.string.people_picker__search_result_team_conversations_header_title, teamName)
        case ContactsSection                               => getString(R.string.people_picker__search_result_connections_header_title)
        case DirectorySection                              => getString(R.string.people_picker__search_result_others_header_title)
        case IntegrationsSection                           => getString(R.string.integrations_picker__section_title)
      }
      sectionHeaderView.setText(title)
    }
  }

}


