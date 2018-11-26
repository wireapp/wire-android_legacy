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
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.content.ReadReceiptsStorage
import com.waz.model.{Liking, RemoteInstant, UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{RefreshingSignal, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ScreenController
import com.waz.zclient.pages.main.conversation.ConversationManagerFragment
import com.waz.zclient.paintcode.{GenericStyleKitView, WireStyleKit}
import com.waz.zclient.participants.ParticipantsAdapter
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils.getStyledColor
import com.waz.zclient.utils.{DateConvertUtils, RichView, ZTimeFormatter}
import com.waz.zclient.{FragmentHelper, R}
import org.threeten.bp.{LocalDateTime, ZoneId}

class LikesAndReadsFragment extends FragmentHelper {
  import LikesAndReadsFragment._

  import Threading.Implicits.Ui
  implicit def ctx: Context = getActivity

  private lazy val zms              = inject[Signal[ZMessaging]]
  private lazy val screenController = inject[ScreenController]

  private val visibleTab = Signal[Tab](ReadsTab)

  private lazy val likes: Signal[Seq[UserId]] = Signal(zms, screenController.showMessageDetails).flatMap {
    case (z, Some(msgId)) =>
      new RefreshingSignal[Seq[UserId], Seq[Liking]](
        CancellableFuture.lift(z.reactionsStorage.getLikes(msgId).map(_.likers.keys.toSeq)),
        z.reactionsStorage.onChanged.map(_.filter(_.message == msgId))
      )
    case _ => Signal.const(Seq.empty[UserId])
  }

  private lazy val reads: Signal[Seq[UserId]] = Signal(zms, screenController.showMessageDetails).flatMap {
    case (z, Some(msgId)) =>
      zms.flatMap(_.readReceiptsStorage.receipts(msgId).map(_.map(_.user)))
    case _ => Signal.const(Seq.empty[UserId])
  }

  private lazy val viewToDisplay = for {
    tab       <- visibleTab
    listEmpty <- if (tab == LikesTab) likes.map(_.isEmpty) else reads.map(_.isEmpty)
  } yield (tab, listEmpty)

  private lazy val message = for {
    z           <- zms
    Some(msgId) <- screenController.showMessageDetails
    msg         <- z.messagesStorage.signal(msgId)
  } yield msg

  private lazy val isOwnMessage = for {
    selfUserId  <- inject[Signal[UserId]]
    msg         <- message
  } yield selfUserId == msg.userId

  private lazy val closeButton = view[GlyphTextView](R.id.likes_close_button)

  private lazy val readsView = returning(view[RecyclerView](R.id.reads_recycler_view)) { vh =>
    viewToDisplay.onUi {
      case (ReadsTab, false) => vh.foreach(_.setVisible(true))
      case _ => vh.foreach(_.setVisible(false))
    }
  }

  private lazy val likesView = returning(view[RecyclerView](R.id.likes_recycler_view)) { vh =>
    viewToDisplay.onUi {
      case (LikesTab, false) => vh.foreach(_.setVisible(true))
      case _ => vh.foreach(_.setVisible(false))
    }
  }

  private lazy val emptyListView = returning(view[View](R.id.empty_list_view)) { vh =>
    val emptyListIcon = findById[GenericStyleKitView](R.id.empty_list_icon)
    emptyListIcon.setColor(getStyledColor(R.attr.wireSecondaryTextColor))
    val emptyListText = findById[TypefaceTextView](R.id.empty_list_text)

    viewToDisplay.onUi {
      case (_, false) =>
        vh.foreach(_.setVisible(false))
      case (ReadsTab, true) =>
        vh.foreach(_.setVisible(true))
        emptyListIcon.setOnDraw(WireStyleKit.drawView)
        emptyListText.setText(R.string.messages_no_reads)
      case (LikesTab, true) =>
        vh.foreach(_.setVisible(true))
        emptyListIcon.setOnDraw(WireStyleKit.drawLike)
        emptyListText.setText(R.string.messages_no_likes)
    }
  }

  private lazy val title = returning(view[TypefaceTextView](R.id.message_details_title)) { vh =>
    isOwnMessage.map {
      case true  => R.string.message_details_title
      case false => R.string.message_likes_title
    }.onUi(resId => vh.foreach(_.setText(resId)))
  }

  private lazy val timestamp = returning(view[TypefaceTextView](R.id.message_timestamp)) { vh =>
    message.onUi { msg =>
      val ts = ZTimeFormatter.getSeparatorTime(getContext, LocalDateTime.now, DateConvertUtils.asLocalDateTime(msg.time.instant), true, ZoneId.systemDefault, true)
      val editTs = ZTimeFormatter.getSeparatorTime(getContext, LocalDateTime.now, DateConvertUtils.asLocalDateTime(msg.editTime.instant), true, ZoneId.systemDefault, true)
      val text =
        s"${getString(R.string.message_details_sent)}: $ts" +
          (if (msg.editTime != RemoteInstant.Epoch) s"\n${getString(R.string.message_details_last_edited)}: $editTs" else "")
      vh.foreach(_.setText(text))
    }
  }

  private lazy val tabs = returning(view[TabLayout](R.id.likes_and_reads_tabs)) { vh =>
    Signal(reads.map(_.size), likes.map(_.size)).onUi { case (r, l) =>
      vh.foreach { view =>
        view.getTabAt(ReadsTab.pos).setText(s"${getString(R.string.tab_title_read)} ($r)")
        view.getTabAt(LikesTab.pos).setText(s"${getString(R.string.tab_title_likes)} ($l)")
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
      ZTimeFormatter.getSeparatorTime(context, LocalDateTime.now, DateConvertUtils.asLocalDateTime(time.instant), true, ZoneId.systemDefault, true)
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

    Signal(screenController.showMessageDetails, isOwnMessage).head.foreach {
      case (Some(msgId), true) =>
        tabs.foreach(_.setVisible(true))

        if (Option(savedInstanceState).isEmpty)
          tabs.foreach(_.getTabAt(Tab(getStringArg(ArgPageToOpen)).pos).select())
        else
          tabs.foreach(_.getTabAt(0).select())
      case _ =>
        tabs.foreach(_.setVisible(false))
        visibleTab ! LikesTab
    }

    closeButton.foreach(_.setOnClickListener(new OnClickListener {
      def onClick(v: View): Unit = onBackPressed()
    }))

    (for {
      receipts    <- inject[Signal[ReadReceiptsStorage]]
      Some(msgId) <- screenController.showMessageDetails
      rs          <- receipts.receipts(msgId)
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

  sealed trait Tab {
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

  private val ArgPageToOpen: String = "ARG_PAGE_TO_OPEN"

  def newInstance(tabToOpen: Tab = ReadsTab): LikesAndReadsFragment =
    returning(new LikesAndReadsFragment) { f =>
      f.setArguments(returning(new Bundle){
        _.putString(ArgPageToOpen, tabToOpen.str)
      })
    }
}

