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
import android.text.Selection
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.TextView.OnEditorActionListener
import android.widget.{CompoundButton, ImageView, TextView}
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.waz.api.Verification
import com.waz.content.UsersStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.SearchQuery
import com.wire.signals._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.ConversationController.getEphemeralDisplayString
import com.waz.zclient.log.LogUI._
import com.waz.zclient.paintcode._
import com.waz.zclient.ui.text.TypefaceEditText.OnSelectionChangedListener
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceEditText, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{Injectable, Injector, R}

import scala.language.postfixOps
import scala.concurrent.duration._
import com.waz.threading.Threading._
import com.wire.signals.ext.ClockSignal

class ParticipantsAdapter(participants:    Signal[Map[UserId, ConversationRole]],
                          maxParticipants: Option[Int] = None,
                          showPeopleOnly:  Boolean = false,
                          showArrow:       Boolean = true,
                          createSubtitle:  Option[(UserData, Boolean) => String] = None
                         )(implicit context: Context, injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ViewHolder] with Injectable with DerivedLogTag {
  import ParticipantsAdapter._

  protected lazy val usersStorage           = inject[Signal[UsersStorage]]
  protected lazy val team                   = inject[Signal[Option[TeamId]]]
  protected lazy val domain                 = inject[Domain]
  protected lazy val participantsController = inject[ParticipantsController]
  protected lazy val convController         = inject[ConversationController]
  protected lazy val themeController        = inject[ThemeController]
  protected lazy val selfId                 = inject[Signal[UserId]]

  private var items               = List.empty[Either[ParticipantData, Int]]
  private var teamDefined         = false
  private var convName            = Option.empty[String]
  private var readReceiptsEnabled = false
  private var convVerified        = false
  private var adminsCount         = 0
  private var membersCount        = 0
  private var botCount            = 0
  private var convNameViewHolder  = Option.empty[ConversationNameViewHolder]

  val onClick                    = EventStream[UserId]()
  val onGuestOptionsClick        = EventStream[Unit]()
  val onEphemeralOptionsClick    = EventStream[Unit]()
  val onShowAllParticipantsClick = EventStream[Unit]()
  val onNotificationsClick       = EventStream[Unit]()
  val onReadReceiptsClick        = EventStream[Unit]()

  val filter = Signal("")

  protected lazy val users: Signal[Vector[ParticipantData]] = for {
    selfId       <- selfId
    usersStorage <- usersStorage
    tId          <- team
    participants <- participants
    users        <- usersStorage.listSignal(participants.keys)
    f            <- filter.throttle(FilterThrottleMs)
  } yield
    users
      .filter(u => f.isEmpty || u.matchesQuery(SearchQuery(f)))
      .map(user => ParticipantData(
        user,
        user.isGuest(tId, domain) && !user.isWireBot,
        isAdmin = participants.get(user.id).contains(ConversationRole.AdminRole),
        isSelf = user.id == selfId
      ))
      .sortBy(_.userData.name.str)

  protected lazy val positions: Signal[List[Either[ParticipantData, Int]]] = for {
    tId         <- team
    users       <- users
    currentConv <- convController.currentConv
    convActive  =  currentConv.isActive
    isTeamConv  =  currentConv.team.nonEmpty
    selfRole    <- participantsController.selfRole
    guestLinks  <- participantsController.areGuestLinksEnabled
  } yield {
    val (bots, people)    = users.toList.partition(_.userData.isWireBot)
    val (admins, members) = people.partition(_.isAdmin)

    adminsCount  = admins.size
    membersCount = members.size
    botCount     = bots.size

    val filteredAdmins  = maxParticipants.fold(admins)(n => if (n >= adminsCount) admins else admins.take(n - 2))
    val filteredMembers = maxParticipants.fold(members)(n => if (n >= people.size) members else members.take(n - adminsCount - 2))
    verbose(l"filter: ${filter.currentValue}, max: $maxParticipants, admins: $adminsCount, filtered admins: ${filteredAdmins.size}, members: $membersCount, filtered members: ${filteredMembers.size}")

    val optionsAvailable = convActive && !showPeopleOnly && (tId.isDefined || selfRole.canModifyMessageTimer || (isTeamConv && (selfRole.canModifyAccess || selfRole.canModifyReceiptMode)))

    (if (!showPeopleOnly) List(Right(if (selfRole.canModifyGroupName) ConversationName else ConversationNameReadOnly)) else Nil) :::
    (if (showPeopleOnly && people.isEmpty) List(Right(NoResultsInfo)) else Nil) :::
    (if (!showPeopleOnly || filteredAdmins.nonEmpty) List(Right(AdminsSeparator)) ::: filteredAdmins.map(data => Left(data)) else Nil) :::
    (if (filteredMembers.nonEmpty) List(Right(MembersSeparator)) ::: filteredMembers.map(data => Left(data)) else Nil) :::
    (if (maxParticipants.exists(_ < people.size)) List(Right(AllParticipants)) else Nil) :::
    (if (optionsAvailable) List(Right(OptionsSeparator)) else Nil) :::
    (if (convActive && !showPeopleOnly && tId.isDefined) List(Right(Notifications)) else Nil) :::
    (if (convActive && !showPeopleOnly && selfRole.canModifyMessageTimer) List(Right(EphemeralOptions)) else Nil) :::
    (if (convActive && !showPeopleOnly && isTeamConv && selfRole.canModifyAccess && guestLinks) List(Right(GuestOptions)) else Nil) :::
    (if (convActive && !showPeopleOnly && isTeamConv && selfRole.canModifyReceiptMode) List(Right(ReadReceipts)) else Nil) :::
    (if (bots.nonEmpty && !showPeopleOnly) List(Right(ServicesSeparator)) else Nil) :::
    (if (showPeopleOnly) Nil else bots.map(data => Left(data)))
  }

  positions.onUi { list =>
    items = list
    notifyDataSetChanged()
  }

  (for {
    conv  <- convController.currentConv
    name  <- convController.currentConvName
    ver   =  conv.verified == Verification.VERIFIED
    read  =  conv.readReceiptsAllowed
    clock <- ClockSignal(5.seconds)
  } yield (name, ver, read, clock)).onUi {
    case (name, ver, read, _) =>
      convName            = Some(name)
      convVerified        = ver
      readReceiptsEnabled = read
      notifyDataSetChanged()
  }

  team.map(_.isDefined).onUi { isTeamDefined =>
    teamDefined = isTeamDefined
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
      view.setTheme(if (themeController.isDarkTheme) Theme.Dark else Theme.Light, background = true)
      ParticipantRowViewHolder(view, onClick)
    case ReadReceipts =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.read_receipts_row, parent, false)
      view.onClick(onReadReceiptsClick ! {})
      ReadReceiptsViewHolder(view, convController)
    case ConversationName =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.conversation_name_row, parent, false)
      returning(ConversationNameViewHolder(view, convController)) { vh =>
        convNameViewHolder = Option(vh)
        vh.setEditingEnabled(true)
      }
    case ConversationNameReadOnly =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.conversation_name_row, parent, false)
      returning(ConversationNameViewHolder(view, convController)) { vh =>
        convNameViewHolder = Option(vh)
        vh.setEditingEnabled(false)
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
    case NoResultsInfo =>
      NoResultsInfoViewHolder(LayoutInflater.from(parent.getContext).inflate(R.layout.participants_no_result_info, parent, false))
    case _ =>
      SeparatorViewHolder(LayoutInflater.from(parent.getContext).inflate(R.layout.participants_separator_row, parent, false))
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = (items(position), holder) match {
    case (Right(AllParticipants), h: ShowAllParticipantsViewHolder) =>
      h.bind(allParticipantsCount)
    case (Left(userData), h: ParticipantRowViewHolder) if userData.isAdmin =>
      val lastRow = maxParticipants.forall(n => if (userData.isAdmin) adminsCount <= n else membersCount <= n) && items.lift(position + 1).forall(_.isRight)
      h.bind(userData, lastRow, createSubtitle, showArrow)
    case (Left(userData), h: ParticipantRowViewHolder) =>
      val lastRow = maxParticipants.forall(membersCount <= _) && items.lift(position + 1).forall(_.isRight)
      h.bind(userData, lastRow, createSubtitle, showArrow)
    case (Right(ReadReceipts), h: ReadReceiptsViewHolder) =>
      h.bind(readReceiptsEnabled)
    case (Right(ConversationName), h: ConversationNameViewHolder) =>
      convName.foreach(name => h.bind(name, convVerified, teamDefined))
    case (Right(ConversationNameReadOnly), h: ConversationNameViewHolder) =>
      convName.foreach(name => h.bind(name, convVerified, teamDefined))
    case (Right(MembersSeparator), h: SeparatorViewHolder) =>
      h.setId(R.id.members_section)
      h.setEmptySection()
      h.setTitle(
        if (showPeopleOnly) getString(R.string.participants_divider_people_no_number)
        else getString(R.string.participants_divider_people, membersCount.toString)
      )
      if (showPeopleOnly) h.setContentDescription(s"Members") else h.setContentDescription(s"Members: $membersCount")
    case (Right(OptionsSeparator), h: SeparatorViewHolder) =>
      h.setId(R.id.options_section)
      h.setTitle(getString(R.string.participants_divider_options))
      h.setEmptySection()
      h.setContentDescription("Options")
    case (Right(ServicesSeparator), h: SeparatorViewHolder) =>
      h.setId(R.id.services_section)
      h.setTitle(getQuantityString(R.plurals.participants_divider_services, botCount, botCount.toString))
      h.setEmptySection()
      h.setContentDescription(s"Services")
    case (Right(AdminsSeparator), h: SeparatorViewHolder) =>
      h.setId(R.id.admins_section)
      h.setEmptySection(if (adminsCount == 0) getString(R.string.participants_no_admins) else "")
      h.setTitle(
        if (showPeopleOnly) getString(R.string.participants_divider_admins_no_number)
        else getString(R.string.participants_divider_admins, adminsCount.toString)
      )
      if (showPeopleOnly) h.setContentDescription(s"Admins") else h.setContentDescription(s"Admins: $adminsCount")
    case _ =>
  }

  protected def allParticipantsCount: Int = membersCount + adminsCount

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
}

object ParticipantsAdapter {
  val FilterThrottleMs: FiniteDuration = 500 millis

  val UserRow                  = 0
  val MembersSeparator         = 1
  val ServicesSeparator        = 2
  val GuestOptions             = 3
  val ConversationName         = 4
  val EphemeralOptions         = 5
  val AllParticipants          = 6
  val Notifications            = 7
  val ReadReceipts             = 8
  val ConversationNameReadOnly = 9
  val OptionsSeparator         = 10
  val AdminsSeparator          = 11
  val NoResultsInfo            = 12

  val separators = Set(AdminsSeparator, MembersSeparator, ServicesSeparator, OptionsSeparator)

  final case class ParticipantData(userData: UserData, isGuest: Boolean, isAdmin: Boolean, isSelf: Boolean)

  final case class GuestOptionsButtonViewHolder(view: View, convController: ConversationController)
                                               (implicit eventContext: EventContext)
    extends ViewHolder(view) {
    private implicit val ctx: Context = view.getContext
    view.setId(R.id.guest_options)
    view.findViewById[TextView](R.id.options_divider).setVisibility(View.VISIBLE)
    view.findViewById[ImageView](R.id.icon).setImageDrawable(GuestIconWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))
    view.findViewById[TextView](R.id.name_text).setText(R.string.guest_options_title)
    convController.currentConv.map(_.isTeamOnly).map {
      case true => getString(R.string.ephemeral_message__timeout__off)
      case false => getString(R.string.guests_option_on)
    }.onUi(view.findViewById[TextView](R.id.value_text).setText)
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    view.setContentDescription("Guest Options")
  }

  final case class EphemeralOptionsButtonViewHolder(view: View, convController: ConversationController)
                                                   (implicit eventContext: EventContext)
    extends ViewHolder(view) {
    private implicit val ctx: Context = view.getContext
    view.setId(R.id.timed_messages_options)
    view.findViewById[ImageView](R.id.icon).setImageDrawable(HourGlassIcon(getStyledColor(R.attr.wirePrimaryTextColor)))
    view.findViewById[TextView](R.id.name_text).setText(R.string.ephemeral_options_title)
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    convController.currentConv.map(_.ephemeralExpiration.flatMap {
      case ConvExpiry(d) => Some(d)
      case _ => None
    }).map(getEphemeralDisplayString)
      .onUi(view.findViewById[TextView](R.id.value_text).setText)
    view.setContentDescription("Ephemeral Options")
  }

  final case class NotificationsButtonViewHolder(view: View, convController: ConversationController)
                                                (implicit eventContext: EventContext)
    extends ViewHolder(view) {
    private implicit val ctx: Context = view.getContext
    view.setId(R.id.notifications_options)
    view.findViewById[ImageView](R.id.icon).setImageDrawable(NotificationsIcon(getStyledColor(R.attr.wirePrimaryTextColor)))
    view.findViewById[TextView](R.id.name_text).setText(R.string.notifications_options_title)
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    convController.currentConv
      .map(c => ConversationController.muteSetDisplayStringId(c.muted))
      .onUi(textId => view.findViewById[TextView](R.id.value_text).setText(textId))
    view.setContentDescription("Notifications")
  }

  final case class SeparatorViewHolder(separator: View) extends ViewHolder(separator) {
    private val textView = ViewUtils.getView[TextView](separator, R.id.separator_title)
    private val emptySectionView = ViewUtils.getView[TextView](separator, R.id.empty_section_info)

    def setId(id: Int): Unit = textView.setId(id)

    def setTitle(title: String = ""): Unit = {
      textView.setText(title)
      textView.setVisible(title.nonEmpty)
    }

    def setEmptySection(text: String = ""): Unit = {
      emptySectionView.setText(text)
      emptySectionView.setVisible(text.nonEmpty)
    }

    def setContentDescription(text: String): Unit = textView.setContentDescription(text)
  }

  final case class NoResultsInfoViewHolder(view: View) extends ViewHolder(view) {
    view.setId(R.id.no_results_info)
    view.setContentDescription(s"No Results")
  }

  final case class ParticipantRowViewHolder(view: SingleUserRowView, onClick: SourceStream[UserId])
    extends ViewHolder(view) {
    private var userId = Option.empty[UserId]

    view.onClick(userId.foreach(onClick ! _))

    def bind(participant:    ParticipantData,
             lastRow:        Boolean,
             createSubtitle: Option[(UserData, Boolean) => String],
             showArrow:      Boolean): Unit = {
      if (participant.isSelf) {
        view.showArrow(false)
        userId = None
      } else {
        view.showArrow(showArrow)
        userId = Some(participant.userData.id)
      }
      createSubtitle match {
        case Some(f) => view.setUserData(participant.userData, createSubtitle = f)
        case None    => view.setUserData(participant.userData)
      }
      view.setSeparatorVisible(!lastRow)
      view.setContentDescription(s"${if (participant.isAdmin) "Admin" else "Member"}: ${participant.userData.name}")
    }
  }

  final case class ReadReceiptsViewHolder(view: View, convController: ConversationController)
                                         (implicit eventContext: EventContext)
    extends ViewHolder(view) {
    private implicit val ctx: Context = view.getContext

    private val switch = view.findViewById[SwitchCompat](R.id.participants_read_receipts_toggle)
    private var readReceipts = Option.empty[Boolean]

    view
      .findViewById[ImageView](R.id.participants_read_receipts_icon)
      .setImageDrawable(ViewWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))
    view.setId(R.id.read_receipts_button)

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

  final case class ConversationNameViewHolder(view: View, convController: ConversationController)
    extends ViewHolder(view) {
    private val callInfo       = view.findViewById[TextView](R.id.call_info)
    private val editText       = view.findViewById[TypefaceEditText](R.id.conversation_name_edit_text)
    private val penGlyph       = view.findViewById[GlyphTextView](R.id.conversation_name_edit_glyph)
    private val verifiedShield = view.findViewById[ImageView](R.id.conversation_verified_shield)

    private var convName = Option.empty[String]
    private var isBeingEdited = false

    def setEditingEnabled(enabled: Boolean): Unit = {
      val penVisibility = if (enabled) View.VISIBLE else View.GONE
      penGlyph.setVisibility(penVisibility)
      editText.setEnabled(enabled)
    }

    private def stopEditing(): Unit = {
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

    def bind(displayName: String, verified: Boolean, isTeam: Boolean)(implicit context: Context): Unit = {
      if (verifiedShield.isVisible != verified) verifiedShield.setVisible(verified)
      if (!convName.contains(displayName)) {
        convName = Some(displayName)
        editText.setText(displayName)
        Selection.removeSelection(editText.getText)
      }

      callInfo.setText(if (isTeam) getString(R.string.call_info, ConversationController.MaxParticipants.toString) else getString(R.string.empty_string))
      callInfo.setMarginTop(getDimenPx(if (isTeam) R.dimen.wire__padding__16 else R.dimen.wire__padding__8)(view.getContext))
      callInfo.setMarginBottom(getDimenPx(if (isTeam) R.dimen.wire__padding__16 else R.dimen.wire__padding__8)(view.getContext))

      callInfo.setContentDescription("Call Info")
    }

    def onBackPressed(): Boolean =
      if (isBeingEdited) {
        convName.foreach(editText.setText)
        stopEditing()
        true
      } else false
  }

  final case class ShowAllParticipantsViewHolder(view: View) extends ViewHolder(view) {
    private implicit val ctx: Context = view.getContext

    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    view.setClickable(true)
    view.setFocusable(true)
    view.setMarginTop(0)
    view.setId(R.id.show_all_button)

    private lazy val nameView = view.findViewById[TypefaceTextView](R.id.name_text)

    def bind(numOfParticipants: Int): Unit = {
      nameView.setText(getString(R.string.show_all_participants, numOfParticipants.toString))
      nameView.setContentDescription(s"Show All: $numOfParticipants")
    }
  }

}

final class LikesAndReadsAdapter(userIds: Signal[Set[UserId]], createSubtitle: Option[UserData => String] = None)
                                (implicit context: Context, injector: Injector, eventContext: EventContext)
  extends ParticipantsAdapter(Signal.empty, None, true, false, createSubtitle.map(f => { (u: UserData, _: Boolean) => f(u) })) {
  import ParticipantsAdapter._

  override protected lazy val users: Signal[Vector[ParticipantData]] = for {
    selfId       <- selfId
    usersStorage <- usersStorage
    tId          <- team
    userIds      <- userIds
    users        <- usersStorage.listSignal(userIds.toList)
  } yield
    users.map(user => ParticipantData(
      user,
      isGuest = user.isGuest(tId, domain) && !user.isWireBot,
      isAdmin = false, // unused
      isSelf  = user.id == selfId
    )).sortBy(_.userData.name.str)

  override protected lazy val positions: Signal[List[Either[ParticipantData, Int]]] = users.map { us =>
    val people = us.toList.filterNot(_.userData.isWireBot)
    if (people.isEmpty) List(Right(NoResultsInfo)) else people.map(data => Left(data))
  }
}
