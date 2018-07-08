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
package com.waz.zclient.conversationlist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.View.{GONE, VISIBLE}
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, LinearLayout}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.ConversationData.ConversationType._
import com.waz.model._
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.views.{ArchiveTopToolbar, ConversationListTopToolbar, NormalTopToolbar}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation
import com.waz.zclient.pages.main.conversationlist.views.ListActionsView
import com.waz.zclient.pages.main.conversationlist.views.ListActionsView.Callback
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.paintcode.DownArrowDrawable
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R, ViewHolder}

/**
  * Due to how we use the NormalConversationListFragment - it gets replaced by the ArchiveConversationListFragment or
  * PickUserFragment, thus destroying its views - we have to be careful about when assigning listeners to signals and
  * trying to instantiate things in onViewCreated - be careful to tear them down again.
  */
abstract class ConversationListFragment extends BaseFragment[ConversationListFragment.Container] with FragmentHelper {

  val layoutId: Int
  lazy val accounts               = inject[AccountsService]
  lazy val userAccountsController = inject[UserAccountsController]
  lazy val conversationController = inject[ConversationController]
  lazy val usersController        = inject[UsersController]
  lazy val screenController       = inject[IConversationScreenController]
  lazy val pickUserController     = inject[IPickUserController]
  lazy val convListController     = inject[ConversationListController]

  protected var subs = Set.empty[Subscription]
  protected val adapterMode: ConversationListAdapter.ListMode

  protected lazy val topToolbar: ViewHolder[_ <: ConversationListTopToolbar] = view[ConversationListTopToolbar](R.id.conversation_list_top_toolbar)
  lazy val adapter = returning(new ConversationListAdapter) { a =>
    a.setMaxAlpha(getResourceFloat(R.dimen.list__swipe_max_alpha))

    userAccountsController.currentUser.onUi(user => topToolbar.get.setTitle(adapterMode, user))

    convListController.conversationListData(adapterMode).onUi {
      case (aId, regular, incoming) => a.setData(regular, incoming)
    }

    a.onConversationClick { conv =>
      verbose(s"handleItemClick, switching conv to $conv")
      conversationController.selectConv(Option(conv), ConversationChangeRequester.CONVERSATION_LIST)
    }

    a.onConversationLongClick { conv =>
      if (Set(Group, OneToOne, WaitForConnection).contains(conv.convType))
        screenController.showConversationMenu(true, conv.id)
    }
  }

  lazy val conversationListView = returning(view[SwipeListView](R.id.conversation_list_view)) { vh =>
    userAccountsController.currentUser.onChanged.onUi(_ => vh.foreach(_.scrollToPosition(0)))
  }

  lazy val conversationsListScrollListener = new RecyclerView.OnScrollListener {
    override def onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = {
      topToolbar.get.setScrolledToTop(!recyclerView.canScrollVertically(-1))
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(layoutId, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    conversationListView.foreach { lv =>
      lv.setLayoutManager(new LinearLayoutManager(getContext))
      lv.setAdapter(adapter)
      lv.setAllowSwipeAway(true)
      lv.setOverScrollMode(View.OVER_SCROLL_NEVER)
      lv.addOnScrollListener(conversationsListScrollListener)
    }
  }

  override def onDestroyView() = {
    conversationListView.foreach(_.removeOnScrollListener(conversationsListScrollListener))
    subs.foreach(_.destroy())
    super.onDestroyView()
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (pickUserController.isHideWithoutAnimations)
      new ConversationListAnimation(0, getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance), enter, 0, 0, false, 1f)
    else if (enter)
      new ConversationListAnimation(0, getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance), enter,
        getInt(R.integer.framework_animation_duration_long), getInt(R.integer.framework_animation_duration_medium), false, 1f)
    else new ConversationListAnimation(0, getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance), enter,
      getInt(R.integer.framework_animation_duration_medium), 0, false, 1f)
  }
}

object ArchiveListFragment{
  val TAG = ArchiveListFragment.getClass.getSimpleName
}

class ArchiveListFragment extends ConversationListFragment with OnBackPressedListener {

  override val layoutId = R.layout.fragment_archive_list
  override lazy val topToolbar = view[ArchiveTopToolbar](R.id.conversation_list_top_toolbar)
  override protected val adapterMode = ConversationListAdapter.Archive

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    topToolbar.foreach(toolbar => subs += toolbar.onRightButtonClick(_ => Option(getContainer).foreach(_.closeArchive())))
  }

  override def onBackPressed() = {
    Option(getContainer).foreach(_.closeArchive())
    true
  }
}

object NormalConversationListFragment {
  val TAG = NormalConversationListFragment.getClass.getSimpleName
}

class NormalConversationFragment extends ConversationListFragment {

  override val layoutId = R.layout.fragment_conversation_list
  override protected val adapterMode = ConversationListAdapter.Normal

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val accentColor = inject[AccentColorController].accentColor
  lazy val incomingClients = for{
    z       <- zms
    clients <- z.otrClientsStorage.incomingClientsSignal(z.selfUserId, z.clientId)
  } yield clients

  private lazy val unreadCount = (for {
    Some(accountId) <- accounts.activeAccountId
    count  <- userAccountsController.unreadCount.map(_.filterNot(_._1 == accountId).values.sum)
  } yield count).orElse(Signal.const(0))

  lazy val hasConversationsAndArchive = for {
    z <- zms
    convs <- z.convsStorage.convsSignal
  } yield {
    (convs.conversations.exists(c => !c.archived && !c.hidden && !Set(Self, Unknown).contains(c.convType)),
    convs.conversations.exists(c => c.archived && !c.hidden && !Set(Self, Unknown).contains(c.convType)))
  }

  lazy val archiveEnabled = hasConversationsAndArchive.map(_._2)

  private val waitingAccount = Signal[Option[UserId]](None)

  lazy val loading = for {
    Some(waitingAcc) <- waitingAccount
    adapterAccount <- convListController.conversationListData(ConversationListAdapter.Normal).map(_._1)
  } yield waitingAcc != adapterAccount

  override lazy val topToolbar = returning(view[NormalTopToolbar](R.id.conversation_list_top_toolbar)) { vh =>
    accentColor.map(_.getColor).onUi(color => vh.foreach(_.setIndicatorColor(color)))
    Signal(unreadCount, incomingClients).onUi {
      case (count, clients) => vh.foreach(_.setIndicatorVisible(clients.nonEmpty || count > 0))
    }
  }

  lazy val loadingListView = view[View](R.id.conversation_list_loading_indicator)
  lazy val listActionsView = returning(view[ListActionsView](R.id.lav__conversation_list_actions)){ vh =>
    archiveEnabled.onUi(enabled => vh.foreach(_.setArchiveEnabled(enabled)))
    hasConversationsAndArchive.map {
      case (false, false) => true
      case _ => false
    }.onUi(centered => vh.foreach(_.setContactsCentered(centered)))
  }

  lazy val noConvsTitle = returning(view[TypefaceTextView](R.id.conversation_list_empty_title)) { vh =>
    hasConversationsAndArchive.map {
      case (false, true) => Some(R.string.all_archived__header)
      case _ => None
    }.onUi(_.foreach(text => vh.foreach(_.setText(text))))
    hasConversationsAndArchive.map {
      case (false, true) => VISIBLE
      case _ => GONE
    }.onUi(visibility => vh.foreach(_.setVisibility(visibility)))
  }

  private lazy val noConvsMessage = returning(view[LinearLayout](R.id.empty_list_message)) { vh =>
    hasConversationsAndArchive.map {
      case (false, false) => VISIBLE
      case _ => GONE
    }.onUi(visibility => vh.foreach(_.setVisibility(visibility)))
  }

  lazy val listActionsScrollListener = new RecyclerView.OnScrollListener {
    override def onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) =
      listActionsView.foreach(_.setScrolledToBottom(!recyclerView.canScrollVertically(1)))
  }

  lazy val listActionsCallback = new Callback {
    override def onAvatarPress() =
      getControllerFactory.getPickUserController.showPickUser()

    override def onArchivePress() =
      Option(getContainer).foreach(_.showArchive())
  }

  override def onViewCreated(v: View, savedInstanceState: Bundle) = {
    super.onViewCreated(v, savedInstanceState)

    for {
      convList <- conversationListView
      actionsList <- listActionsView
    } yield {
      convList.addOnScrollListener(listActionsScrollListener)
      actionsList.setCallback(listActionsCallback)
      actionsList.setScrolledToBottom(!convList.canScrollVertically(1))
    }

    subs += loading.onUi {
      case true => showLoading()
      case false =>
        hideLoading()
        waitingAccount ! None
    }

    topToolbar.foreach { toolbar =>
      subs += toolbar.onRightButtonClick(_ =>
        getActivity.startActivityForResult(PreferencesActivity.getDefaultIntent(getContext), PreferencesActivity.SwitchAccountCode)
      )
    }


    val pickUserController = inject[IPickUserController]
    noConvsMessage.foreach(_.onClick(pickUserController.showPickUser()))

    //initialise lazy vals
    loadingListView
    noConvsTitle

    Option(findById[ImageView](v, R.id.empty_list_arrow)).foreach { v =>
      val drawable = DownArrowDrawable()
      v.setImageDrawable(drawable)
      drawable.setColor(Color.WHITE)
      drawable.setAlpha(102)
    }
  }

  override def onDestroyView() = {
    conversationListView.foreach(_.removeOnScrollListener(listActionsScrollListener))
    listActionsView.foreach(_.setCallback(null))
    super.onDestroyView()
  }

  private def showLoading(): Unit = {
    conversationListView.foreach(_.setVisibility(View.INVISIBLE))
    loadingListView.foreach { lv =>
      lv.setAlpha(1f)
      lv.setVisibility(VISIBLE)
    }
    listActionsView.foreach(_.setAlpha(0.5f))
    topToolbar.foreach(_.setLoading(true))
  }

  private def hideLoading(): Unit = {
    conversationListView.foreach { lv =>
      if (lv.getVisibility != VISIBLE) {
        lv.setVisibility(VISIBLE)
        lv.setAlpha(0f)
        lv.animate().alpha(1f).setDuration(500)
      }
    }
    listActionsView.foreach(_.animate().alpha(1f).setDuration(500))
    loadingListView.foreach(_.animate().alpha(0f).setDuration(500).withEndAction(new Runnable {
      override def run() = loadingListView.foreach(_.setVisibility(GONE))
    }))

    topToolbar.foreach(_.setLoading(false))
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    if (requestCode == PreferencesActivity.SwitchAccountCode && data != null) {
      showLoading()
      waitingAccount ! Some(UserId(data.getStringExtra(PreferencesActivity.SwitchAccountExtra)))
    }
  }
}


object ConversationListFragment {
  trait Container {
    def showArchive(): Unit
    def closeArchive(): Unit
  }

  def newNormalInstance(): ConversationListFragment = {
    new NormalConversationFragment()
  }

  def newArchiveInstance(): ConversationListFragment = {
    new ArchiveListFragment()
  }
}
