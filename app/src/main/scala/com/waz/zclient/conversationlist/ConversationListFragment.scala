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
import android.view.View.{GONE, VISIBLE}
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, LinearLayout}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.content.UserPreferences
import com.waz.model.ConversationData.ConversationType._
import com.waz.model._
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.ConversationListController._
import com.waz.zclient.conversationlist.adapters.{ArchiveConversationListAdapter, ConversationFolderListAdapter, ConversationListAdapter, NormalConversationListAdapter}
import com.waz.zclient.conversationlist.views.{ArchiveTopToolbar, ConversationListTopToolbar, NormalTopToolbar}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation
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
  protected val adapterMode: ListMode

  protected lazy val topToolbar: ViewHolder[_ <: ConversationListTopToolbar] = view[ConversationListTopToolbar](R.id.conversation_list_top_toolbar)

  lazy val adapter: ConversationListAdapter = returning(createAdapter())(configureAdapter)

  lazy val conversationListView = returning(view[SwipeListView](R.id.conversation_list_view)) { vh =>
    subs += userAccountsController.currentUser.onChanged.onUi(_ => vh.foreach(_.scrollToPosition(0)))
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
    super.onDestroyView()
  }

  override def onDestroy(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onDestroy()
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

  protected def createAdapter(): ConversationListAdapter

  private def configureAdapter(adapter: ConversationListAdapter): Unit = {
    adapter.setMaxAlpha(getResourceFloat(R.dimen.list__swipe_max_alpha))
    
    subs += userAccountsController.currentUser.onUi(user => topToolbar.get.setTitle(adapterMode, user))

    subs += adapter.onConversationClick { conv =>
      verbose(l"handleItemClick, switching conv to $conv")
      conversationController.selectConv(Option(conv), ConversationChangeRequester.CONVERSATION_LIST)
    }

    subs += adapter.onConversationLongClick { conv =>
      if (Set(Group, OneToOne, WaitForConnection).contains(conv.convType))
        screenController.showConversationMenu(true, conv.id)
    }
  }
}

object ConversationListFragment {
  trait Container {
    def onConversationsLoadingStarted(): Unit
    def onConversationsLoadingFinished(): Unit
    def closeArchive(): Unit
  }

  def newNormalInstance(): ConversationListFragment = new NormalConversationFragment()
  def newArchiveInstance(): ConversationListFragment = new ArchiveListFragment()
  def newFoldersInstance(): ConversationListFragment = new ConversationFolderListFragment()
}

class ArchiveListFragment extends ConversationListFragment with OnBackPressedListener {

  override val layoutId = R.layout.fragment_archive_list
  override lazy val topToolbar = view[ArchiveTopToolbar](R.id.conversation_list_top_toolbar)
  override protected val adapterMode = Archive

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    topToolbar.foreach(toolbar => subs += toolbar.onRightButtonClick(_ => Option(getContainer).foreach(_.closeArchive())))
  }

  override def onBackPressed() = {
    Option(getContainer).foreach(_.closeArchive())
    true
  }

  override protected def createAdapter(): ConversationListAdapter =
    returning(new ArchiveConversationListAdapter) { a =>
      subs += convListController.archiveConversationListData.onUi { archive =>
        a.setData(archive)
      }
  }
}

object ArchiveListFragment {
  val TAG = ArchiveListFragment.getClass.getSimpleName
}

class NormalConversationFragment extends ConversationListFragment {
  override val layoutId: Int = R.layout.fragment_conversation_list
  override protected val adapterMode: ListMode = Normal

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val accentColor = inject[AccentColorController].accentColor
  lazy val incomingClients = for {
    z       <- zms
    clients <- z.otrClientsStorage.incomingClientsSignal(z.selfUserId, z.clientId)
  } yield clients

  private lazy val readReceiptsChanged = zms.flatMap(_.userPrefs(UserPreferences.ReadReceiptsRemotelyChanged).signal)

  private lazy val unreadCount = (for {
    Some(accountId) <- accounts.activeAccountId
    count  <- userAccountsController.unreadCount.map(_.filterNot(_._1 == accountId).values.sum)
  } yield count).orElse(Signal.const(0))

  private val waitingAccount = Signal[Option[UserId]](None)

  private lazy val loading = for {
    Some(waitingAcc) <- waitingAccount
    z                <- zms
    processing       <- z.push.processing
  } yield processing || waitingAcc != z.selfUserId

  override lazy val topToolbar = returning(view[NormalTopToolbar](R.id.conversation_list_top_toolbar)) { vh =>
    subs += accentColor.map(_.color).onUi(color => vh.foreach(_.setIndicatorColor(color)))
    subs += Signal(unreadCount, incomingClients, readReceiptsChanged).onUi {
      case (count, clients, rrChanged) => vh.foreach(_.setIndicatorVisible(clients.nonEmpty || count > 0 || rrChanged))
    }
  }

  lazy val loadingListView = view[View](R.id.conversation_list_loading_indicator)

  lazy val noConvsTitle = returning(view[TypefaceTextView](R.id.conversation_list_empty_title)) { vh =>
    subs += convListController.hasConversationsAndArchive.map {
      case (false, true) => Some(R.string.all_archived__header)
      case _ => None
    }.onUi(_.foreach(text => vh.foreach(_.setText(text))))
    subs += convListController.hasConversationsAndArchive.map {
      case (false, true) => VISIBLE
      case _ => GONE
    }.onUi(visibility => vh.foreach(_.setVisibility(visibility)))
  }

  private lazy val noConvsMessage = returning(view[LinearLayout](R.id.empty_list_message)) { vh =>
    subs += convListController.hasConversationsAndArchive.map {
      case (false, false) => VISIBLE
      case _ => GONE
    }.onUi(visibility => vh.foreach(_.setVisibility(visibility)))
  }

  override def onViewCreated(v: View, savedInstanceState: Bundle) = {
    super.onViewCreated(v, savedInstanceState)

    subs += loading.onUi {
      case true => showLoading()
      case false =>
        hideLoading()
        waitingAccount ! None
    }

    topToolbar.foreach { toolbar =>
      subs += toolbar.onRightButtonClick { _ =>
        getActivity.startActivityForResult(PreferencesActivity.getDefaultIntent(getContext), PreferencesActivity.SwitchAccountCode)
      }
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

  private def showLoading(): Unit = {
    loadingListView.foreach { lv =>
      lv.setAlpha(1f)
      lv.setVisibility(VISIBLE)
    }
    Option(getContainer).foreach(_.onConversationsLoadingStarted())
    topToolbar.foreach(_.setLoading(true))
  }

  private def hideLoading(): Unit = {
    Option(getContainer).foreach(_.onConversationsLoadingFinished())
    loadingListView.foreach(v => v.animate().alpha(0f).setDuration(500).withEndAction(new Runnable {
      override def run() = {
        if (NormalConversationFragment.this != null)
          v.setVisibility(GONE)
      }
    }))

    topToolbar.foreach(_.setLoading(false))
  }


  override protected def createAdapter(): ConversationListAdapter =
    returning(new NormalConversationListAdapter) { a =>
      val dataSource = for {
        regular  <- convListController.regularConversationListData
        incoming <- convListController.incomingConversationListData
      } yield (regular, incoming)

      subs += dataSource.onUi { case (regular, incoming) =>
        a.setData(regular, incoming)
      }
    }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    if (requestCode == PreferencesActivity.SwitchAccountCode && data != null) {
      showLoading()
      waitingAccount ! Some(UserId(data.getStringExtra(PreferencesActivity.SwitchAccountExtra)))
    }
  }
}

object NormalConversationFragment {
  val TAG = "NormalConversationFragment"
}

class ConversationFolderListFragment extends NormalConversationFragment {

  override protected val adapterMode: ListMode = Folders

  override protected def createAdapter(): ConversationListAdapter = {
    returning(new ConversationFolderListAdapter) { a =>
      val dataSource = for {
        incoming  <- convListController.incomingConversationListData
        favorites <- convListController.favoriteConversations
        groups    <- convListController.groupConvsWithoutFolder
        oneToOnes <- convListController.oneToOneConvsWithoutFolder
        custom    <- convListController.customFolderConversations
        states    <- convListController.folderStateController.folderUiStates
      } yield (incoming, favorites, groups, oneToOnes, custom, states)

      subs += dataSource.onUi { case (incoming, favorites, groups, oneToOnes, custom, states) =>
        a.setData(incoming, favorites, groups, oneToOnes, custom, states)
      }

      subs += a.onFoldersChanged.onUi(convListController.folderStateController.prune)

      subs += a.onFolderStateChanged.onUi { case (id, isExpanded) =>
        convListController.folderStateController.update(id, isExpanded)
      }
    }
  }
}

object ConversationFolderListFragment {
  val TAG = "ConversationFolderListFragment"
}
