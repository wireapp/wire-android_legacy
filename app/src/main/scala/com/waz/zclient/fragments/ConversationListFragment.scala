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
package com.waz.zclient.fragments

import android.os.Bundle
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.View.OnClickListener
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.api.VoiceChannel
import com.waz.model.ConversationData
import com.waz.model.ConversationData.ConversationType
import com.waz.model.otr.Client
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.adapters.ConversationListAdapter
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.controllers.tracking.events.navigation.OpenedContactsEvent
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation
import com.waz.zclient.pages.main.conversationlist.views.ListActionsView
import com.waz.zclient.pages.main.conversationlist.views.ListActionsView.Callback
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.pages.main.profile.ZetaPreferencesActivity
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.ui.utils.ResourceUtils
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.conversationlist.ConversationListTopToolbar
import com.waz.zclient.{BaseActivity, FragmentHelper, OnBackPressedListener, R}

abstract class ConversationListFragment extends BaseFragment[ConversationListFragment.Container] with FragmentHelper with VoiceChannel.JoinCallback {

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    val view = inflater.inflate(R.layout.fragment_conversation_list, container, false)
    val conversationListView = ViewUtils.getView(view, R.id.conversation_list_view).asInstanceOf[SwipeListView]
    val adapter = new ConversationListAdapter(getContext)
    val topToolbar = ViewUtils.getView(view, R.id.conversation_list_top_toolbar).asInstanceOf[ConversationListTopToolbar]
    val listActionsView = ViewUtils.getView(view, R.id.lav__conversation_list_actions).asInstanceOf[ListActionsView]
    val emptyConversationsView = ViewUtils.getView(view, R.id.ll__conversation_list__no_contacts).asInstanceOf[View]

    conversationListView.setLayoutManager(new LinearLayoutManager(getContext))
    conversationListView.setAdapter(adapter)
    conversationListView.setAllowSwipeAway(true)

    conversationListView.addOnScrollListener(new RecyclerView.OnScrollListener {
      override def onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = {
        listActionsView.setScrolledToBottom(!recyclerView.canScrollVertically(1))
        topToolbar.setScrolledToTop(!recyclerView.canScrollVertically(-1))
      }
    })

    adapter.onConversationClick { handleItemClick }
    adapter.onConversationLongClick { handleItemLongClick(_, conversationListView) }
    adapter.setMaxAlpha(ResourceUtils.getResourceFloat(getResources, R.dimen.list__swipe_max_alpha))

    listActionsView.setCallback(new Callback {
      override def onAvatarPress() = {
        getControllerFactory.getPickUserController.showPickUser(IPickUserController.Destination.CONVERSATION_LIST, null)
      }

      override def onArchivePress() = {
        conversationListView.stopScroll()
        Option(getContainer).foreach(_.showArchive())
      }
    })

    adapter.currentMode.on(Threading.Ui) { mode =>
      topToolbar.title.setText(mode.nameId)
    }

    adapter.itemCount.on(Threading.Ui) {
      case 0 =>
        emptyConversationsView.setVisibility(View.VISIBLE)
      case _ =>
        emptyConversationsView.setVisibility(View.GONE)
    }

    init(adapter, listActionsView, topToolbar)

    view
  }

  def init(adapter: ConversationListAdapter, listActionsView: ListActionsView, topToolbar: ConversationListTopToolbar): Unit

  private def handleItemClick(conversationData: ConversationData): Unit = {
    val iConversation = getStoreFactory.getConversationStore.getConversation(conversationData.id.str)
    getControllerFactory.getLoadTimeLoggerController.clickConversationInList()

    val conversationChangeRequester =
      if (conversationData.archived)
        ConversationChangeRequester.CONVERSATION_LIST_UNARCHIVED_CONVERSATION
      else
        ConversationChangeRequester.CONVERSATION_LIST

    getStoreFactory.getConversationStore.setCurrentConversation(iConversation, conversationChangeRequester)
  }

  private def handleItemLongClick(conversationData: ConversationData, anchorView: View): Unit = {
    if (conversationData.convType != ConversationType.Group &&
        conversationData.convType != ConversationType.OneToOne &&
        conversationData.convType != ConversationType.WaitForConnection) {
      return
    }
    val iConversation = getStoreFactory.getConversationStore.getConversation(conversationData.id.str)
    getControllerFactory.getConversationScreenController.showConversationMenu(IConversationScreenController.CONVERSATION_LIST_LONG_PRESS, iConversation, anchorView)
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0 || getContainer == null || getControllerFactory.isTornDown)
      return super.onCreateAnimation(transit, enter, nextAnim)

    if (getControllerFactory.getPickUserController.isHideWithoutAnimations)
      return new ConversationListAnimation(
        0,
        getResources.getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        0,
        0,
        false,
        1f)

    if (enter)
      return new ConversationListAnimation(
        0,
        getResources.getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getResources.getInteger(R.integer.framework_animation_duration_long),
        getResources.getInteger(R.integer.framework_animation_duration_medium),
        false,
        1f)

    new ConversationListAnimation(
      0,
      getResources.getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getResources.getInteger(R.integer.framework_animation_duration_medium),
      0,
      false,
      1f)
  }

  override def onCallJoined() = {}

  override def onAlreadyJoined() = {}

  override def onCallJoinError(message: String) = {}

  override def onConversationTooBig(memberCount: Int, maxMembers: Int) = {
    ViewUtils.showAlertDialog(
      getActivity,
      getString(R.string.calling__conversation_full__title),
      getResources.getQuantityString(R.plurals.calling__conversation_full__message, maxMembers, new Integer(maxMembers)),
      getString(R.string.alert_dialog__confirmation),
      null,
      false)
  }

  override def onVoiceChannelFull(maxJoined: Int) = {
    ViewUtils.showAlertDialog(
      getActivity,
      getString(R.string.calling__voice_channel_full__title),
      getResources.getQuantityString(R.plurals.calling__voice_channel_full__message, maxJoined, new Integer(maxJoined)),
      getString(R.string.alert_dialog__confirmation),
      null,
      false)
  }
}

object ArchiveListFragment{
  val TAG = ArchiveListFragment.getClass.getSimpleName
}
class ArchiveListFragment extends ConversationListFragment with OnBackPressedListener {
  override def init(adapter: ConversationListAdapter, listActionsView: ListActionsView, topToolbar: ConversationListTopToolbar): Unit ={
    adapter.currentMode ! ConversationListAdapter.Archive
    listActionsView.setVisibility(View.GONE)
    topToolbar.setClose()
    topToolbar.glyphButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View) = {
        Option(getContainer).foreach(_.closeArchive())
      }
    })
  }

  override def onBackPressed() = {
    Option(getContainer).foreach(_.closeArchive())
    true
  }
}

object NormalConversationListFragment{
  val TAG = NormalConversationListFragment.getClass.getSimpleName
}
class NormalConversationFragment extends ConversationListFragment {

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val accentColor = inject[AccentColorController].accentColor
  lazy val incomingClients = for{
    z <- zms
    color <- accentColor
    clients <- z.account.account.clientId.fold(Signal.empty[Seq[Client]])(aid => z.otrClientsStorage.incomingClientsSignal(z.selfUserId, aid))
  } yield (color.getColor(), clients)

  lazy val hasArchive = for {
    z <- zms
    convs <- z.convsStorage.convsSignal
  } yield convs.conversations.exists(c => c.archived && !c.hidden)

  override def init(adapter: ConversationListAdapter, listActionsView: ListActionsView, topToolbar: ConversationListTopToolbar): Unit = {
    adapter.currentMode ! ConversationListAdapter.Normal

    hasArchive.on(Threading.Ui) { listActionsView.setArchiveEnabled }

    topToolbar.glyphButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View) = {
        startActivity(ZetaPreferencesActivity.getDefaultIntent(getContext))
      }
    })

    incomingClients.on(Threading.Ui) {
      case (color, clients) =>
        topToolbar.setIndicatorVisible(clients.nonEmpty)
        topToolbar.setIndicatorColor(color)
    }
  }
}

object ConversationListFragment {
  trait Container {
    def showArchive()
    def closeArchive()
  }

  def newNormalInstance(): ConversationListFragment = {
    new NormalConversationFragment()
  }

  def newArchiveInstance(): ConversationListFragment = {
    new ArchiveListFragment()
  }
}
