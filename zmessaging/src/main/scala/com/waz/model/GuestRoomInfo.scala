package com.waz.model

sealed trait GuestRoomInfo
case class ConversationOverview(name: String) extends GuestRoomInfo
case class ExistingConversation(conversationData: ConversationData) extends GuestRoomInfo
