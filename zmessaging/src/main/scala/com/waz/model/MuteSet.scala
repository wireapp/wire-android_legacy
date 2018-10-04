/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.model

import MuteSet._

case class MuteSet(private val status: Set[MuteMask]) {
  def reverse: MuteSet = MuteSet(MuteSet.AllMuted.status -- status)

  def toInt: Int =
    (if (status.contains(StandardMuted)) 2 else 0) | (if (status.contains(MentionsMuted)) 1 else 0)

  lazy val isAllMuted: Boolean          = status == AllMuted
  lazy val isAllAllowed: Boolean        = status == AllAllowed
  lazy val onlyMentionsAllowed: Boolean = status == OnlyMentionsAllowed

  def oldMutedFlag: Boolean = status.contains(StandardMuted)
}

object MuteSet {
  sealed trait MuteMask
  case object StandardMuted extends MuteMask
  case object MentionsMuted extends MuteMask

  val AllMuted: MuteSet            = MuteSet(Set[MuteMask](StandardMuted, MentionsMuted))
  val OnlyMentionsAllowed: MuteSet = MuteSet(Set[MuteMask](MentionsMuted)).reverse
  val AllAllowed: MuteSet          = MuteSet(Set.empty[MuteMask])

  // 0 -> `00` -> All notifications are displayed
  // 1 -> `01` -> Only normal notifications are displayed (mentions are muted) -- not used
  // 2 -> `10` -> Only mentions are displayed (normal messages muted)
  // 3 -> `11` -> No notifications are displayed
  def apply(status: Int): MuteSet =
    MuteSet(
      (if ((status & 1) != 0) Set[MuteMask](MentionsMuted) else Set.empty[MuteMask]) ++
      (if ((status & 2) != 0) Set[MuteMask](StandardMuted) else Set.empty[MuteMask])
    )

  def resolveMuted(convState: ConversationState): MuteSet = (convState.muted, convState.mutedStatus) match {
    case (Some(true),  None)         => OnlyMentionsAllowed
    case (Some(true),  Some(status)) => MuteSet(status | 2)
    case (Some(false), _)            => AllAllowed
    case (None,        Some(status)) => MuteSet(status)
    case (None,        None)         => AllAllowed
  }
}
