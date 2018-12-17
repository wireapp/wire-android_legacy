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
package com.waz.zclient.conversation

import android.content.Context
import android.os.Bundle
import android.support.annotation.Nullable
import android.support.design.widget.TabLayout
import android.support.design.widget.TabLayout.OnTabSelectedListener
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.content.{MessagesStorage, ReactionsStorage, ReadReceiptsStorage}
import com.waz.model.{MessageData, RemoteInstant, UserData, UserId}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ScreenController.MessageDetailsParams
import com.waz.zclient.common.controllers.{ScreenController, UserAccountsController}
import com.waz.zclient.messages.LikesController
import com.waz.zclient.pages.main.conversation.ConversationManagerFragment
import com.waz.zclient.paintcode.{GenericStyleKitView, WireStyleKit}
import com.waz.zclient.participants.ParticipantsAdapter
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils.getColor
import com.waz.zclient.utils.{RichView, ZTimeFormatter}
import com.waz.zclient.{FragmentHelper, R}
import org.threeten.bp.DateTimeUtils

class LikesAndReadsFragment extends FragmentHelper {
  import LikesAndReadsFragment._
  import Threading.Implicits.Ui
  implicit def ctx: Context = getActivity

  private lazy val screenController    = inject[ScreenController]
  private lazy val readReceiptsStorage = inject[Signal[ReadReceiptsStorage]]
  private lazy val reactionsStorage    = inject[Signal[ReactionsStorage]]
  private lazy val messagesStorage     = inject[Signal[MessagesStorage]]
  private lazy val accountsController  = inject[UserAccountsController]

  private val visibleTab = Signal[Tab](ReadsTab)

  private lazy val likes: Signal[Seq[UserId]] =
    Signal(reactionsStorage, screenController.showMessageDetails)
      .collect { case (storage, Some(msgId)) => (storage, msgId) }
      .flatMap { case (storage, MessageDetailsParams(msgId, _)) => storage.likes(msgId).map(_.likers.keys.toSeq) }

  private lazy val reads: Signal[Seq[UserId]] =
    Signal(readReceiptsStorage, screenController.showMessageDetails)
      .collect { case (storage, Some(MessageDetailsParams(msgId, _))) => (storage, msgId) }
      .flatMap { case (storage, msgId) => storage.receipts(msgId).map(_.map(_.user)) }

  private lazy val message = for {
    messagesStorage <- messagesStorage
    Some(msgParams) <- screenController.showMessageDetails
    msg             <- messagesStorage.signal(msgParams.messageId)
  } yield msg

  private lazy val viewToDisplay: Signal[ViewToDisplay] =
    for {
      msg       <- message
      tab       <- visibleTab
      listEmpty <- if (tab == LikesTab) likes.map(_.isEmpty) else reads.map(_.isEmpty)
    } yield (msg.expectsRead.contains(true), tab, listEmpty) match {
      case (_, ReadsTab, false)    => ReadsTab
      case (false, ReadsTab, true) => ReadsOff
      case (_, ReadsTab, true)     => NoReads
      case (_, LikesTab, false)    => LikesTab
      case (_, LikesTab, true)     => NoLikes
    }

  private lazy val isOwnMessage = for {
    selfUserId  <- inject[Signal[UserId]]
    msg         <- message
  } yield selfUserId == msg.userId

  private lazy val isEphemeral = message.map(_.isEphemeral)

  private lazy val isLikeable = message.map(LikesController.isLikeable)

  private lazy val detailsCombination = Signal(message, isOwnMessage, accountsController.isTeam).map {
    case (msg, isOwn, isTeam) => LikesAndReadsFragment.detailsCombination(msg, isOwn, isTeam)
  }

  private lazy val closeButton = view[GlyphTextView](R.id.likes_close_button)

  private lazy val readsView = returning(view[RecyclerView](R.id.reads_recycler_view)) { vh =>
    Signal(viewToDisplay, detailsCombination).onUi {
      case (ReadsTab, JustReads | ReadsAndLikes) => vh.foreach(_.setVisible(true))
      case _ => vh.foreach(_.setVisible(false))
    }
  }

  private lazy val likesView = returning(view[RecyclerView](R.id.likes_recycler_view)) { vh =>
    Signal(viewToDisplay, detailsCombination).onUi {
      case (LikesTab, JustLikes | ReadsAndLikes) => vh.foreach(_.setVisible(true))
      case _ => vh.foreach(_.setVisible(false))
    }
  }

  private lazy val emptyListView = returning(view[View](R.id.empty_list_view)) { vh =>
    val emptyListIcon = findById[GenericStyleKitView](R.id.empty_list_icon)
    emptyListIcon.setColor(getColor(R.color.light_graphite_16))
    val emptyListText = findById[TypefaceTextView](R.id.empty_list_text)

    Signal(viewToDisplay, detailsCombination).onUi {
      case (NoReads, JustReads | ReadsAndLikes) =>
        vh.foreach(_.setVisible(true))
        emptyListIcon.setOnDraw(WireStyleKit.drawView)
        emptyListText.setText(R.string.messages_no_reads)
      case (ReadsOff, JustReads | ReadsAndLikes) =>
        vh.foreach(_.setVisible(true))
        emptyListIcon.setOnDraw(WireStyleKit.drawView)
        emptyListText.setText(R.string.messages_reads_turned_off)
      case (NoLikes, JustLikes | ReadsAndLikes) =>
        vh.foreach(_.setVisible(true))
        emptyListIcon.setOnDraw(WireStyleKit.drawLike)
        emptyListText.setText(R.string.messages_no_likes)
      case _ =>
        vh.foreach(_.setVisible(false))
    }
  }

  private lazy val title = returning(view[TypefaceTextView](R.id.message_details_title)) { vh =>
    detailsCombination.map {
      case ReadsAndLikes => Some(R.string.message_details_title)
      case JustReads     => Some(R.string.message_read_title)
      case JustLikes     => Some(R.string.message_liked_title)
      case NoDetails     => None
    }.onUi {
      case Some(resId) => vh.foreach(_.setText(resId))
      case None =>
    }
  }

  private lazy val timestamp = returning(view[TypefaceTextView](R.id.message_timestamp)) { vh =>
    message.onUi { msg =>
      val ts     = ZTimeFormatter.getSingleMessageDateTime(getContext, DateTimeUtils.toDate(msg.time.instant))
      val editTs = ZTimeFormatter.getSingleMessageDateTime(getContext, DateTimeUtils.toDate(msg.editTime.instant))
      val text =
        s"${getString(R.string.message_details_sent)}: $ts" +
          (if (msg.editTime != RemoteInstant.Epoch) s"\n${getString(R.string.message_details_last_edited)}: $editTs" else "")
      vh.foreach(_.setText(text))
    }
  }

  private lazy val tabs = returning(view[TabLayout](R.id.likes_and_reads_tabs)) { vh =>
    Signal(reads.map(_.size), likes.map(_.size)).map {
      case (r, l) =>
        val rCountString = if (r == 0) "" else s" ($r)"
        val lCountString = if (l == 0) "" else s" ($l)"
        (rCountString, lCountString)
    }
      .onUi { case (r, l) =>
      vh.foreach { view =>
        view.getTabAt(ReadsTab.pos).setText(s"${getString(R.string.tab_title_read)}$r")
        view.getTabAt(LikesTab.pos).setText(s"${getString(R.string.tab_title_likes)}$l")
      }
    }

    vh.foreach {
      _.addOnTabSelectedListener(new OnTabSelectedListener {
        override def onTabSelected(tab: TabLayout.Tab): Unit = {
          visibleTab ! Tab.tabs.find(_.pos == tab.getPosition).getOrElse(ReadsTab)
        }

        override def onTabUnselected(tab: TabLayout.Tab): Unit = {}
        override def onTabReselected(tab: TabLayout.Tab): Unit = {}
      })
    }
  }

  private var readTimestamps = Map.empty[UserId, RemoteInstant]

  private def createSubtitle(user: UserData)(implicit context: Context): String =
    readTimestamps.get(user.id).fold("")(time =>
      ZTimeFormatter.getSingleMessageDateTime(context, DateTimeUtils.toDate(time.instant))
    )

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_likes_and_reads, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    title
    timestamp
    closeButton
    readsView
    likesView
    emptyListView
    tabs

   readsView.foreach { rv =>
      rv.setLayoutManager(new LinearLayoutManager(getContext))
      rv.setAdapter(new ParticipantsAdapter(reads, createSubtitle = Some(createSubtitle), showPeopleOnly = true, showArrow = false))
    }

    likesView.foreach { rv =>
      rv.setLayoutManager(new LinearLayoutManager(getContext))
      rv.setAdapter(new ParticipantsAdapter(likes, showPeopleOnly = true, showArrow = false))
    }

    detailsCombination.head.foreach {
      case ReadsAndLikes =>
        tabs.foreach(_.setVisible(true))

        if (Option(savedInstanceState).isEmpty)
          tabs.foreach(_.getTabAt(Tab(getStringArg(ArgPageToOpen)).pos).select())
        else
          tabs.foreach(_.getTabAt(0).select())
      case JustReads =>
        tabs.foreach(_.setVisible(false))
        visibleTab ! ReadsTab
      case JustLikes =>
        tabs.foreach(_.setVisible(false))
        visibleTab ! LikesTab
      case NoDetails =>
        ZLog.error("NoDetails chosen as the details combination - the fragment should not be opened at all")
        tabs.foreach(_.setVisible(false))
    }

    closeButton.foreach(_.setOnClickListener(new OnClickListener {
      def onClick(v: View): Unit = onBackPressed()
    }))

    (for {
      receipts        <- readReceiptsStorage
      Some(msgParams) <- screenController.showMessageDetails
      rs              <- receipts.receipts(msgParams.messageId)
    } yield rs.map(r => r.user -> r.timestamp).toMap).onUi {
      readTimestamps = _
    }
  }

  override def onBackPressed(): Boolean = Option(getParentFragment) match {
    case Some(f: ConversationManagerFragment) =>
      screenController.showMessageDetails ! None
      true
    case _ => false
  }
}

object LikesAndReadsFragment {
  val Tag = implicitLogTag

  sealed trait ViewToDisplay

  case object NoReads  extends ViewToDisplay
  case object ReadsOff extends ViewToDisplay
  case object NoLikes  extends ViewToDisplay

  sealed trait Tab extends ViewToDisplay {
    val str: String
    val pos: Int
  }

  case object ReadsTab extends Tab {
    override val str: String = s"${classOf[LikesAndReadsFragment].getName}/reads"
    override val pos: Int = 0
  }

  case object LikesTab extends Tab {
    override val str: String = s"${classOf[LikesAndReadsFragment].getName}/likes"
    override val pos: Int = 1
  }

  object Tab {
    val tabs = List(ReadsTab, LikesTab)
    def apply(str: Option[String] = None): Tab = str match {
      case Some(LikesTab.str) => LikesTab
      case _ => ReadsTab
    }
  }

  sealed trait DetailsCombination
  case object JustLikes extends DetailsCombination
  case object JustReads extends DetailsCombination
  case object ReadsAndLikes extends DetailsCombination
  case object NoDetails extends DetailsCombination

  private val ArgPageToOpen: String = "ARG_PAGE_TO_OPEN"

  def newInstance(tabToOpen: Tab = ReadsTab): LikesAndReadsFragment =
    returning(new LikesAndReadsFragment) { f =>
      f.setArguments(returning(new Bundle){
        _.putString(ArgPageToOpen, tabToOpen.str)
      })
    }

  def detailsCombination(message: MessageData, isOwnMessage: Boolean, isTeam: Boolean): DetailsCombination =
    (isOwnMessage, !message.isEphemeral && LikesController.isLikeable(message), isTeam) match {
      case (true, true, true)  => ReadsAndLikes
      case (true, false, true) => JustReads
      case (_, true, _)        => JustLikes
      case _                   => NoDetails
  }
}

