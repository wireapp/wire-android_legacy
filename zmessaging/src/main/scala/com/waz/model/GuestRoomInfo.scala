package com.waz.model

sealed trait GuestRoomInfo
object GuestRoomInfo {
  case class Overview(name: String) extends GuestRoomInfo
  case class ExistingConversation(conversationData: ConversationData) extends GuestRoomInfo
}

sealed trait GuestRoomStateError
object GuestRoomStateError {
  object NotAllowed extends GuestRoomStateError
  object MemberLimitReached extends GuestRoomStateError
  object GeneralError extends GuestRoomStateError
}
