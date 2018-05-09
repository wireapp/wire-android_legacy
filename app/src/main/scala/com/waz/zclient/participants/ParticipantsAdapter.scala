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
package com.waz.zclient.participants

import android.content.Context
import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ViewHolder
import android.text.Selection
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView.OnEditorActionListener
import android.widget.{ImageView, TextView}
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.api.Verification
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.common.views.SingleUserRowView.Theme
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.paintcode.{ForwardNavigationIcon, GuestIconWithColor}
import com.waz.zclient.ui.text.TypefaceEditText.OnSelectionChangedListener
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceEditText}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{ContextUtils, RichView, ViewUtils}
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.duration._

class ParticipantsAdapter(numOfColumns: Int)(implicit context: Context, injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ViewHolder] with Injectable {
  import ParticipantsAdapter._

  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController         = inject[ConversationController]
  private lazy val themeController        = inject[ThemeController]

  private var items        = List.empty[Either[ParticipantData, Int]]
  private var teamId       = Option.empty[TeamId]
  private var convId       = Option.empty[ConvId]
  private var convName     = Option.empty[String]
  private var convVerified = false

  private var convNameViewHolder = Option.empty[ConversationNameViewHolder]

  val onClick             = EventStream[UserId]()
  val onGuestOptionsClick = EventStream[Unit]()

  lazy val users = for {
    z       <- zms
    userIds <- participantsController.otherParticipants.map(_.toSeq)
    users   <- Signal.sequence(userIds.filterNot(_ == z.selfUserId).map(z.users.userSignal): _*)
  } yield users.map(u => ParticipantData(u, u.isGuest(z.teamId) && !u.isWireBot)).sortBy(_.userData.getDisplayName)

  private val shouldShowGuestButton = inject[ConversationController].currentConv.map(_.accessRole.isDefined)

  private lazy val positions = for {
    users       <- users
    isTeam      <- participantsController.currentUserBelongsToConversationTeam
    guestButton <- shouldShowGuestButton
  } yield {
    val (bots, people) = users.toList.partition(_.userData.isWireBot)

    List(Right(ConversationName)) :::
    (if (isTeam && guestButton) List(Right(GuestOptions))
      else Nil
      ) :::
    (if (people.nonEmpty) List(Right(PeopleSeparator))
      else Nil
      ) ::: people.map(data => Left(data)) :::
    (if (bots.nonEmpty) List(Right(BotsSeparator))
      else Nil
        ) ::: bots.map(data => Left(data))
  }

  positions.onUi { list =>
    items = list
    notifyDataSetChanged()
  }

  val conv = convController.currentConv

  (for {
    id   <- conv.map(_.id)
    name <- conv.map(_.displayName)
    ver  <- conv.map(_.verified == Verification.VERIFIED)
    clock <- ClockSignal(5.seconds)
  } yield (id, name, ver, clock)).onUi {
    case (id, name, ver, _) =>
      convId = Some(id)
      convName = Some(name)
      convVerified = ver
      notifyDataSetChanged()
  }

  zms.map(_.teamId).onUi { tId =>
    teamId = tId
    notifyDataSetChanged()
  }

  def onBackPressed(): Boolean = convNameViewHolder.exists(_.onBackPressed())

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case GuestOptions =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.list_options_button, parent, false)
      view.onClick(onGuestOptionsClick ! {})
      GuestOptionsButtonViewHolder(view)
    case UserRow =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.single_user_row, parent, false).asInstanceOf[SingleUserRowView]
      view.showArrow(true)
      view.setTheme(if (themeController.isDarkTheme) Theme.Dark else Theme.Light)
      ParticipantRowViewHolder(view, onClick)
    case ConversationName =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.conversation_name_row, parent, false)
      returning(ConversationNameViewHolder(view, zms)) { vh =>
        convNameViewHolder = Option(vh)
      }
    case _ => SeparatorViewHolder(getSeparatorView(parent))
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = (items(position), holder) match {
    case (Left(userData), h: ParticipantRowViewHolder)            => h.bind(userData, teamId, items.lift(position + 1).forall(_.isRight))
    case (Right(ConversationName), h: ConversationNameViewHolder) => for (id <- convId; name <- convName) h.bind(id, name, convVerified)
    case (Right(sepType), h: SeparatorViewHolder) if Set(PeopleSeparator, BotsSeparator).contains(sepType) =>
      val count = items.count {
        case Left(a)
          if sepType == PeopleSeparator && !a.userData.isWireBot ||
             sepType == BotsSeparator && a.userData.isWireBot => true
        case _ => false
      }.toString
      h.setTitle(getString(if (sepType == PeopleSeparator) R.string.participants_divider_people else R.string.participants_divider_services, count))
      h.setId(if (sepType == PeopleSeparator) R.id.participants_section else R.id.services_section)

    case _ =>
  }

  def getSpanSize(position: Int): Int = getItemViewType(position) match {
    case PeopleSeparator | BotsSeparator => numOfColumns
    case _                               => 1
  }

  override def getItemCount: Int = items.size

  override def getItemId(position: Int): Long = items(position) match {
    case Left(user)     => user.userData.id.hashCode()
    case Right(sepType) => sepType
  }

  setHasStableIds(true)

  override def getItemViewType(position: Int): Int = items(position) match {
    case Right(sepType) => sepType
    case _              => UserRow
  }

  private def getSeparatorView(parent: ViewGroup): View =
    LayoutInflater.from(parent.getContext).inflate(R.layout.participants_separator_row, parent, false)

}

object ParticipantsAdapter {
  val UserRow          = 0
  val PeopleSeparator  = 1
  val BotsSeparator    = 2
  val GuestOptions     = 3
  val ConversationName = 4

  case class ParticipantData(userData: UserData, isGuest: Boolean)

  case class GuestOptionsButtonViewHolder(view: View) extends ViewHolder(view) {
    private implicit val ctx = view.getContext
    //view.setId(R.id.guest_options)
    view.findViewById[ImageView](R.id.icon).setImageDrawable(GuestIconWithColor(ContextUtils.getStyledColor(R.attr.wirePrimaryTextColor)))
    view.findViewById[TextView](R.id.name_text).setText(R.string.guest_options_title)
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
  }

  case class SeparatorViewHolder(separator: View) extends ViewHolder(separator) {
    private val textView = ViewUtils.getView[TextView](separator, R.id.separator_title)

    def setTitle(title: String) = textView.setText(title)
    def setId(id: Int) = textView.setId(id)
  }

  case class ParticipantRowViewHolder(view: SingleUserRowView, onClick: SourceStream[UserId]) extends ViewHolder(view) {

    private var userId = Option.empty[UserId]

    view.onClick(userId.foreach(onClick ! _))

    def bind(participant: ParticipantData, teamId: Option[TeamId], lastRow: Boolean): Unit = {
      userId = Some(participant.userData.id)
      view.setUserData(participant.userData, teamId)
      view.setSeparatorVisible(!lastRow)
    }
  }

  case class ConversationNameViewHolder(view: View, zms: Signal[ZMessaging]) extends ViewHolder(view) {
    private val editText = view.findViewById[TypefaceEditText](R.id.conversation_name_edit_text)
    private val penGlyph = view.findViewById[GlyphTextView](R.id.conversation_name_edit_glyph)
    private val verifiedShield = view.findViewById[ImageView](R.id.conversation_verified_shield)

    private var convId = Option.empty[ConvId]
    private var convName = Option.empty[String]

    private var isBeingEdited = false

    private def stopEditing() = {
      editText.setSelected(false)
      editText.clearFocus()
      Selection.removeSelection(editText.getText)
    }

    editText.setAccentColor(Color.BLACK)

    editText.setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          stopEditing()
          convId.foreach { c =>
            import Threading.Implicits.Background
            zms.head.flatMap(_.convsUi.setConversationName(c, v.getText.toString))
          }
        }
        false
      }
    })

    editText.setOnSelectionChangedListener(new OnSelectionChangedListener {
      override def onSelectionChanged(selStart: Int, selEnd: Int): Unit = {
        isBeingEdited = selStart > 0
        penGlyph.animate().alpha(if (selStart >= 0) 0.0f else 1.0f).start()
      }
    })

    def bind(id: ConvId, displayName: String, verified: Boolean): Unit = {
      if (!convId.contains(id)) convId = Some(id)
      if (verifiedShield.isVisible != verified) verifiedShield.setVisible(verified)
      if (!convName.contains(displayName)) {
        convName = Some(displayName)
        editText.setText(displayName)
        Selection.removeSelection(editText.getText)
      }
    }

    def onBackPressed(): Boolean =
      if (isBeingEdited) {
        convName.foreach(editText.setText)
        stopEditing()
        true
      } else false
  }

}
