package com.waz.model

sealed trait JoinConversationInfo
case class ConversationOverview(name: String) extends JoinConversationInfo
case class ExistingConversation(conversationData: ConversationData) extends JoinConversationInfo
