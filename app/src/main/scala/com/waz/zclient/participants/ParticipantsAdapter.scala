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
import com.waz.service.{SearchKey, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.ConversationController.getEphemeralDisplayString
import com.waz.zclient.paintcode.{ForwardNavigationIcon, GuestIconWithColor, HourGlassIcon}
import com.waz.zclient.ui.text.TypefaceEditText.OnSelectionChangedListener
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceEditText, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.duration._

//TODO: Good target for refactoring. The adapter and its data should be separated.
class ParticipantsAdapter(maxParticipants: Option[Int] = None, showPeopleOnly: Boolean = false)(implicit context: Context, injector: Injector, eventContext: EventContext)
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
  private var peopleCount  = 0

  private var convNameViewHolder = Option.empty[ConversationNameViewHolder]

  val onClick             = EventStream[UserId]()
  val onGuestOptionsClick = EventStream[Unit]()
  val onEphemeralOptionsClick = EventStream[Unit]()
  val onShowAllParticipantsClick = EventStream[Unit]()
  val filter = Signal("")

  lazy val users = for {
    z       <- zms
    userIds <- participantsController.otherParticipants.map(_.toSeq)
    users   <- Signal.sequence(userIds.filterNot(_ == z.selfUserId).map(z.usersStorage.signal): _*)
    searchKey <- filter.map(SearchKey(_))
    //TODO: this filtering logic should be extracted
    filteredUsers = users.filter(u => searchKey.isAtTheStartOfAnyWordIn(u.searchKey) || u.handle.exists(_.startsWithQuery(searchKey.asciiRepresentation)))
  } yield filteredUsers.map(u => ParticipantData(u, u.isGuest(z.teamId) && !u.isWireBot)).sortBy(_.userData.getDisplayName)

  private val shouldShowGuestButton = inject[ConversationController].currentConv.map(_.accessRole.isDefined)

  private lazy val positions = for {
    users       <- users
    isTeam      <- participantsController.currentUserBelongsToConversationTeam
    convActive  <- convController.currentConv.map(_.isActive)
    guestButton <- shouldShowGuestButton
    areWeAGuest <- participantsController.isCurrentUserGuest
  } yield {
    val (bots, people) = users.toList.partition(_.userData.isWireBot)

    peopleCount = people.size

    val filteredPeople = maxParticipants.filter(_ < peopleCount).fold {
      people
    } { mp =>
      people.take(mp - 2)
    }

    (if (!showPeopleOnly) List(Right(ConversationName)) else Nil) :::
    (if (convActive && isTeam && guestButton && !showPeopleOnly) List(Right(GuestOptions))
      else Nil
      ) :::
    (if (convActive && !areWeAGuest && !showPeopleOnly) List(Right(EphemeralOptions))
      else Nil
        ) :::
    (if (people.nonEmpty && !showPeopleOnly) List(Right(PeopleSeparator))
      else Nil
      ) :::
    filteredPeople.map(data => Left(data)) :::
    (if (maxParticipants.exists(peopleCount > _)) List(Right(AllParticipants))
      else Nil) :::
    (if (bots.nonEmpty && !showPeopleOnly) List(Right(BotsSeparator))
      else Nil
        ) :::
     (if (showPeopleOnly) Nil else bots.map(data => Left(data)))
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
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.list_options_button_with_value_label, parent, false)
      view.onClick(onGuestOptionsClick ! {})
      GuestOptionsButtonViewHolder(view, convController)
    case UserRow =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.single_user_row, parent, false).asInstanceOf[SingleUserRowView]
      view.showArrow(true)
      view.setTheme(if (themeController.isDarkTheme) Theme.Dark else Theme.Light, background = true)
      ParticipantRowViewHolder(view, onClick)
    case ConversationName =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.conversation_name_row, parent, false)
      returning(ConversationNameViewHolder(view, zms)) { vh =>
        convNameViewHolder = Option(vh)
      }
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
    case (Right(AllParticipants), h: ShowAllParticipantsViewHolder) => h.bind(peopleCount)
    case (Left(userData), h: ParticipantRowViewHolder)            => h.bind(userData, teamId, maxParticipants.forall(peopleCount <= _) && items.lift(position + 1).forall(_.isRight))
    case (Right(ConversationName), h: ConversationNameViewHolder) => for (id <- convId; name <- convName) h.bind(id, name, convVerified, teamId.isDefined)
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
  val EphemeralOptions = 5
  val AllParticipants  = 6

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
    private val callInfo = view.findViewById[TextView](R.id.call_info)
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

    def bind(id: ConvId, displayName: String, verified: Boolean, isTeam: Boolean): Unit = {
      if (!convId.contains(id)) convId = Some(id)
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
    view.findViewById[ImageView](R.id.icon).setImageDrawable(GuestIconWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))
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
