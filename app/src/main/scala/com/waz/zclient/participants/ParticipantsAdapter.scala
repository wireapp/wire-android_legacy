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
import android.support.v7.widget.{RecyclerView, SwitchCompat}
import android.support.v7.widget.RecyclerView.ViewHolder
import android.text.Selection
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.TextView.OnEditorActionListener
import android.widget.{CompoundButton, ImageView, TextView}
import com.waz.api.Verification
import com.waz.model._
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.ConversationController.getEphemeralDisplayString
import com.waz.zclient.paintcode._
import com.waz.zclient.ui.text.TypefaceEditText.OnSelectionChangedListener
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceEditText, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.duration._
import com.waz.content.UsersStorage

class ParticipantsAdapter(userIds: Signal[Seq[UserId]],
                          maxParticipants: Option[Int] = None,
                          showPeopleOnly: Boolean = false,
                          showArrow: Boolean = true,
                          createSubtitle: Option[(UserData) => String] = None
                         )(implicit context: Context, injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ViewHolder] with Injectable {
  import ParticipantsAdapter._

  private lazy val usersStorage = inject[Signal[UsersStorage]]
  private lazy val team         = inject[Signal[Option[TeamId]]]

  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController         = inject[ConversationController]
  private lazy val themeController        = inject[ThemeController]

  private var items               = List.empty[Either[ParticipantData, Int]]
  private var teamId              = Option.empty[TeamId]
  private var convName            = Option.empty[String]
  private var readReceiptsEnabled = false
  private var convVerified        = false
  private var peopleCount         = 0
  private var botCount            = 0

  private var convNameViewHolder = Option.empty[ConversationNameViewHolder]

  val onClick                    = EventStream[UserId]()
  val onGuestOptionsClick        = EventStream[Unit]()
  val onEphemeralOptionsClick    = EventStream[Unit]()
  val onShowAllParticipantsClick = EventStream[Unit]()
  val onNotificationsClick       = EventStream[Unit]()
  val onReadReceiptsClick        = EventStream[Unit]()
  val filter = Signal("")

  lazy val users = for {
    usersStorage  <- usersStorage
    tId           <- team
    userIds       <- userIds
    users         <- usersStorage.listSignal(userIds)
    f             <- filter
    filteredUsers = users.filter(_.matchesFilter(f))
  } yield filteredUsers.map(u => ParticipantData(u, u.isGuest(tId) && !u.isWireBot)).sortBy(_.userData.getDisplayName.str)

  private val shouldShowGuestButton = inject[ConversationController].currentConv.map(_.accessRole.isDefined)

  private lazy val positions = for {
    tId         <- team
    users       <- users
    isTeam      <- participantsController.currentUserBelongsToConversationTeam
    convActive  <- convController.currentConv.map(_.isActive)
    guestButton <- shouldShowGuestButton
    areWeAGuest <- participantsController.isCurrentUserGuest
  } yield {
    val (bots, people) = users.toList.partition(_.userData.isWireBot)

    peopleCount = people.size
    botCount = bots.size

    val filteredPeople = maxParticipants.filter(_ < peopleCount).fold {
      people
    } { mp =>
      people.take(mp - 2)
    }

    (if (!showPeopleOnly) List(Right(ConversationName)) else Nil) :::
    (if (convActive && tId.isDefined && !showPeopleOnly) List(Right(Notifications))
    else Nil
      ) :::
    (if (convActive && !areWeAGuest && !showPeopleOnly) List(Right(EphemeralOptions))
      else Nil
        ) :::
    (if (convActive && isTeam && guestButton && !showPeopleOnly) List(Right(GuestOptions))
    else Nil
      ) :::
    (if (convActive && isTeam && !showPeopleOnly) List(Right(ReadReceipts))
    else Nil
      ) :::
    (if (people.nonEmpty && !showPeopleOnly) List(Right(PeopleSeparator))
      else Nil
      ) :::
    filteredPeople.map(data => Left(data)) :::
    (if (maxParticipants.exists(peopleCount > _)) List(Right(AllParticipants))
      else Nil) :::
    (if (bots.nonEmpty && !showPeopleOnly) List(Right(ServicesSeparator))
      else Nil
        ) :::
     (if (showPeopleOnly) Nil else bots.map(data => Left(data)))
  }

  positions.onUi { list =>
    items = list
    notifyDataSetChanged()
  }

  private val conv = convController.currentConv

  (for {
    name  <- conv.map(_.displayName)
    ver   <- conv.map(_.verified == Verification.VERIFIED)
    read  <- conv.map(_.readReceiptsAllowed)
    clock <- ClockSignal(5.seconds)
  } yield (name, ver, read, clock)).onUi {
    case (name, ver, read, _) =>
      convName            = Some(name)
      convVerified        = ver
      readReceiptsEnabled = read
      notifyDataSetChanged()
  }

  team.onUi { tId =>
    teamId = tId
    notifyDataSetChanged()
  }

  def onBackPressed(): Boolean = convNameViewHolder.exists(_.onBackPressed())

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case GuestOptions =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.list_options_button_with_value_label, parent, false)
      view.onClick(onGuestOptionsClick ! {})
      GuestOptionsButtonViewHolder(view, convController)
    case UserRow =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.single_user_row, parent, false).asInstanceOf[SingleUserRowView]
      view.showArrow(showArrow)
      view.setTheme(if (themeController.isDarkTheme) Theme.Dark else Theme.Light, background = true)
      ParticipantRowViewHolder(view, onClick)
    case ReadReceipts =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.read_receipts_row, parent, false)
      ReadReceiptsViewHolder(view, convController)
    case ConversationName =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.conversation_name_row, parent, false)
      returning(ConversationNameViewHolder(view, convController)) { vh =>
        convNameViewHolder = Option(vh)
      }
    case Notifications =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.list_options_button_with_value_label, parent, false)
      view.onClick(onNotificationsClick ! {})
      NotificationsButtonViewHolder(view, convController)
    case EphemeralOptions =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.list_options_button_with_value_label, parent, false)
      view.onClick(onEphemeralOptionsClick ! {})
      EphemeralOptionsButtonViewHolder(view, convController)
    case AllParticipants =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.list_options_button, parent, false)
      view.onClick(onShowAllParticipantsClick ! {})
      ShowAllParticipantsViewHolder(view)
    case _ => SeparatorViewHolder(getSeparatorView(parent))
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = (items(position), holder) match {
    case (Right(AllParticipants), h: ShowAllParticipantsViewHolder) =>
      h.bind(peopleCount)
    case (Left(userData), h: ParticipantRowViewHolder) =>
      h.bind(userData, teamId, maxParticipants.forall(peopleCount <= _) && items.lift(position + 1).forall(_.isRight), createSubtitle)
    case (Right(ReadReceipts), h: ReadReceiptsViewHolder) =>
      h.bind(readReceiptsEnabled)
    case (Right(ConversationName), h: ConversationNameViewHolder) =>
      convName.foreach(name => h.bind(name, convVerified, teamId.isDefined))
    case (Right(sepType), h: SeparatorViewHolder) if Set(PeopleSeparator, ServicesSeparator).contains(sepType) =>
      val count = if (sepType == PeopleSeparator) peopleCount else botCount
      h.setTitle(getString(if (sepType == PeopleSeparator) R.string.participants_divider_people else R.string.participants_divider_services, count.toString))
      h.setId(if (sepType == PeopleSeparator) R.id.participants_section else R.id.services_section)
    case _ =>
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
  val UserRow           = 0
  val PeopleSeparator   = 1
  val ServicesSeparator = 2
  val GuestOptions      = 3
  val ConversationName  = 4
  val EphemeralOptions  = 5
  val AllParticipants   = 6
  val Notifications     = 7
  val ReadReceipts      = 8

  case class ParticipantData(userData: UserData, isGuest: Boolean)

  case class GuestOptionsButtonViewHolder(view: View, convController: ConversationController)(implicit eventContext: EventContext) extends ViewHolder(view) {
    private implicit val ctx = view.getContext
    view.setId(R.id.guest_options)
    view.findViewById[TextView](R.id.options_divider).setVisibility(View.VISIBLE)
    view.findViewById[ImageView](R.id.icon).setImageDrawable(GuestIconWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))
    view.findViewById[TextView](R.id.name_text).setText(R.string.guest_options_title)
    convController.currentConv.map(_.isTeamOnly).map {
      case true => getString(R.string.ephemeral_message__timeout__off)
      case false => getString(R.string.guests_option_on)
    }.onUi(view.findViewById[TextView](R.id.value_text).setText)
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
  }

  case class EphemeralOptionsButtonViewHolder(view: View, convController: ConversationController)(implicit eventContext: EventContext) extends ViewHolder(view) {
    private implicit val ctx = view.getContext
    view.setId(R.id.timed_messages_options)
    view.findViewById[ImageView](R.id.icon).setImageDrawable(HourGlassIcon(getStyledColor(R.attr.wirePrimaryTextColor)))
    view.findViewById[TextView](R.id.name_text).setText(R.string.ephemeral_options_title)
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    convController.currentConv.map(_.ephemeralExpiration.flatMap {
      case ConvExpiry(d) => Some(d)
      case _ => None
    }).map(getEphemeralDisplayString)
      .onUi(view.findViewById[TextView](R.id.value_text).setText)
  }

  case class NotificationsButtonViewHolder(view: View, convController: ConversationController)(implicit eventContext: EventContext) extends ViewHolder(view) {
    private implicit val ctx = view.getContext
    view.setId(R.id.notifications_options)
    view.findViewById[ImageView](R.id.icon).setImageDrawable(NotificationsIcon(getStyledColor(R.attr.wirePrimaryTextColor)))
    view.findViewById[TextView](R.id.name_text).setText(R.string.notifications_options_title)
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    convController.currentConv
      .map(c => ConversationController.muteSetDisplayStringId(c.muted))
      .onUi(textId => view.findViewById[TextView](R.id.value_text).setText(textId))
  }

  case class SeparatorViewHolder(separator: View) extends ViewHolder(separator) {
    private val textView = ViewUtils.getView[TextView](separator, R.id.separator_title)

    def setTitle(title: String) = textView.setText(title)
    def setId(id: Int) = textView.setId(id)
  }

  case class ParticipantRowViewHolder(view: SingleUserRowView, onClick: SourceStream[UserId]) extends ViewHolder(view) {

    private var userId = Option.empty[UserId]

    view.onClick(userId.foreach(onClick ! _))

    def bind(participant: ParticipantData, teamId: Option[TeamId], lastRow: Boolean, createSubtitle: Option[(UserData) => String]): Unit = {
      userId = Some(participant.userData.id)
      createSubtitle match {
        case Some(f) => view.setUserData(participant.userData, teamId, f)
        case None    => view.setUserData(participant.userData, teamId)
      }
      view.setSeparatorVisible(!lastRow)
    }
  }

  case class ReadReceiptsViewHolder(view: View, convController: ConversationController)(implicit eventContext: EventContext) extends ViewHolder(view) {
    private implicit val ctx = view.getContext

    private val switch = view.findViewById[SwitchCompat](R.id.participants_read_receipts_toggle)
    private var readReceipts = Option.empty[Boolean]

    view.findViewById[ImageView](R.id.participants_read_receipts_icon).setImageDrawable(ViewWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))

    switch.setOnCheckedChangeListener(new OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, readReceiptsEnabled: Boolean): Unit =
        if (!readReceipts.contains(readReceiptsEnabled)) {
          readReceipts = Some(readReceiptsEnabled)
          convController.setCurrentConvReadReceipts(readReceiptsEnabled)
        }
    })

    def bind(readReceiptsEnabled: Boolean): Unit =
      if (!readReceipts.contains(readReceiptsEnabled)) switch.setChecked(readReceiptsEnabled)
  }

  case class ConversationNameViewHolder(view: View, convController: ConversationController) extends ViewHolder(view) {
    private val callInfo = view.findViewById[TextView](R.id.call_info)
    private val editText = view.findViewById[TypefaceEditText](R.id.conversation_name_edit_text)
    private val penGlyph = view.findViewById[GlyphTextView](R.id.conversation_name_edit_glyph)
    private val verifiedShield = view.findViewById[ImageView](R.id.conversation_verified_shield)

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
          convController.setCurrentConvName(v.getText.toString)
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

    def bind(displayName: String, verified: Boolean, isTeam: Boolean): Unit = {
      if (verifiedShield.isVisible != verified) verifiedShield.setVisible(verified)
      if (!convName.contains(displayName)) {
        convName = Some(displayName)
        editText.setText(displayName)
        Selection.removeSelection(editText.getText)
      }

      callInfo.setText(if (isTeam) R.string.call_info_text else R.string.empty_string)
      callInfo.setMarginTop(getDimenPx(if (isTeam) R.dimen.wire__padding__16 else R.dimen.wire__padding__8)(view.getContext))
      callInfo.setMarginBottom(getDimenPx(if (isTeam) R.dimen.wire__padding__16 else R.dimen.wire__padding__8)(view.getContext))
    }

    def onBackPressed(): Boolean =
      if (isBeingEdited) {
        convName.foreach(editText.setText)
        stopEditing()
        true
      } else false
  }

  case class ShowAllParticipantsViewHolder(view: View) extends ViewHolder(view) {
    private implicit val ctx: Context = view.getContext
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    view.setClickable(true)
    view.setFocusable(true)
    view.setMarginTop(0)
    private lazy val nameView = view.findViewById[TypefaceTextView](R.id.name_text)

    def bind(numOfParticipants: Int): Unit = {
      nameView.setText(getString(R.string.show_all_participants, numOfParticipants.toString))
    }
  }

}
