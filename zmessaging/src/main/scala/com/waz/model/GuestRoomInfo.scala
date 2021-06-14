package com.waz.model

import com.waz.api.impl.ErrorResponse

sealed trait GuestRoomInfo
object GuestRoomInfo {
  case class Overview(name: String) extends GuestRoomInfo
  case class ExistingConversation(conversationData: ConversationData) extends GuestRoomInfo
}

sealed trait JoinConversationResult
object JoinConversationResult {
  object Success extends JoinConversationResult
  object CannotJoin extends JoinConversationResult
  object MemberLimitReached extends JoinConversationResult
  case class GeneralError(error: ErrorResponse) extends JoinConversationResult
}
