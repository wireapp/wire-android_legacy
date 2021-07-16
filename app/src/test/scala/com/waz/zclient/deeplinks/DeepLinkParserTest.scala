package com.waz.zclient.deeplinks


import java.util.UUID

import com.waz.model.{ConvId, UserId}
import com.waz.zclient.deeplinks.DeepLink.{ConversationToken, RawToken, SSOLoginToken, UserToken}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class DeepLinkParserTest extends JUnitSuite {

  @Test
  def parseLink_ssoLogin_returnsSsoLoginDeepLink(): Unit = {
    val uuid = "test-uuid-1234"
    val deepLink = s"wire://start-sso/wire-$uuid"

    val parsedLink = DeepLinkParser.parseLink(deepLink)

    val expectedToken = RawToken(s"wire-$uuid")
    assert(parsedLink.contains((DeepLink.SSOLogin, expectedToken)))
  }

  @Test
  def parseLink_conversation_returnsConversationDeepLink(): Unit = {
    val convId = "test-conv-id-1234"
    val deepLink = s"wire://conversation/$convId"

    val parsedLink = DeepLinkParser.parseLink(deepLink)

    val expectedToken = RawToken(s"$convId")
    assert(parsedLink.contains((DeepLink.Conversation, expectedToken)))
  }

  @Test
  def parseLink_conversation_returnsJoinConversationDeepLink(): Unit = {
    val key = "0q1VerOvOmu33z6Zrej4"
    val code = "WHQicv4TurzL64K-tEZs"
    val deepLink = s"wire://conversation-join?key=$key&code=$code"

    val parsedLink = DeepLinkParser.parseLink(deepLink)

    val expectedToken = RawToken(deepLink) // JoinConversation should have the whole like as the token
    assert(parsedLink.contains((DeepLink.JoinConversation, expectedToken)))
  }

  @Test
  def parseLink_user_returnsUserDeepLink(): Unit = {
    val userId = "test-user-id-1234"
    val deepLink = s"wire://user/$userId"

    val parsedLink = DeepLinkParser.parseLink(deepLink)

    val expectedToken = RawToken(s"$userId")
    assert(parsedLink.contains((DeepLink.User, expectedToken)))
  }

  @Test
  def parseLink_customBackend_returnsAccessDeepLink(): Unit = {
    val customBackendUrl = "test-url"
    val deepLink = s"wire://access?config=$customBackendUrl"

    val parsedLink = DeepLinkParser.parseLink(deepLink)

    val expectedToken = RawToken(s"?config=$customBackendUrl")
    assert(parsedLink.contains((DeepLink.Access, expectedToken)))
  }

  @Test
  def parseLink_invalidScheme_returnsNone(): Unit = {
    val deepLink = s"dummy://host/path"

    val parsedLink = DeepLinkParser.parseLink(deepLink)

    assert(parsedLink.isEmpty)
  }

  @Test
  def parseLink_unrecognizedHost_returnsNone(): Unit = {
    val deepLink = s"wire://unknownhost/path"

    val parsedLink = DeepLinkParser.parseLink(deepLink)

    assert(parsedLink.isEmpty)
  }

  @Test
  def parseToken_ssoLogin_validId_returnsSSOLoginToken(): Unit = {
    val uuid = UUID.randomUUID().toString
    val tokenValue = s"wire-$uuid"
    val rawToken = RawToken(tokenValue)

    val parsedToken = DeepLinkParser.parseToken(DeepLink.SSOLogin, rawToken)

    assert(parsedToken.contains(SSOLoginToken(tokenValue)))
  }

  @Test
  def parseToken_ssoLogin_invalidId_returnsNone(): Unit = {
    val uuid = "invalid uuid format"
    val tokenValue = s"wire-$uuid"
    val rawToken = RawToken(tokenValue)

    val parsedToken = DeepLinkParser.parseToken(DeepLink.SSOLogin, rawToken)

    assert(parsedToken.isEmpty)
  }

  @Test
  def parseToken_userLink_validId_returnsUserToken(): Unit = {
    val userId = UserId()
    val rawToken = RawToken(userId.str)

    val parsedToken = DeepLinkParser.parseToken(DeepLink.User, rawToken)

    assert(parsedToken.contains(UserToken(userId)))
  }

  @Test
  def parseToken_userLink_invalidId_returnsNone(): Unit = {
    val userId = "invalid id format"
    val rawToken = RawToken(userId)

    val parsedToken = DeepLinkParser.parseToken(DeepLink.User, rawToken)

    assert(parsedToken.isEmpty)
  }

  @Test
  def parseToken_conversationLink_validId_returnsConversationToken(): Unit = {
    val convId = ConvId()
    val rawToken = RawToken(convId.str)

    val parsedToken = DeepLinkParser.parseToken(DeepLink.Conversation, rawToken)

    assert(parsedToken.contains(ConversationToken(convId)))
  }

  @Test
  def parseToken_conversationLink_invalidId_returnsNone(): Unit = {
    val convId = "invalid id format"
    val rawToken = RawToken(convId)

    val parsedToken = DeepLinkParser.parseToken(DeepLink.Conversation, rawToken)

    assert(parsedToken.isEmpty)
  }

  //TODO: add parseToken test cases for CustomBackendToken
}
