package com.waz.service.messages

import com.waz.content.{ButtonsStorage, ConversationStorage, MessagesStorage, MsgDeletionStorage, UserPreferences}
import com.waz.model.{ButtonData, ButtonId, ConvId, ConversationData, MessageData, MessageId}
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MessagesContentUpdaterSpec extends AndroidFreeSpec {

  private lazy val storage      =  mock[MessagesStorage]
  private lazy val convsStorage =  mock[ConversationStorage]
  private lazy val deletions    =  mock[MsgDeletionStorage]
  private lazy val buttons      =  mock[ButtonsStorage]
  private lazy val prefs        =  new TestGlobalPreferences()
  private lazy val userPrefs    =  new TestUserPreferences()

  scenario("Confirm a button action") {
    val messageId = MessageId()
    val buttonId = ButtonId()
    val buttonData = ButtonData(messageId, buttonId, "title", 0, ButtonData.ButtonWaiting)
    val confirmation = Map(messageId -> Option(buttonId))

    // the tested method does not return anything so instead we check if the button is modified in the storage
    (buttons.findByMessage _).expects(messageId).atLeastOnce().returning(Future.successful(Seq(buttonData)))
    (buttons.updateAll2 _).expects(Seq((messageId, buttonId)), *).atLeastOnce().returning(Future.successful(Nil))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)

    result(updater.updateButtonConfirmations(confirmation))
  }

  scenario("Confirm a button action when there are more than one button") {
    val messageId = MessageId()
    val buttonId1 = ButtonId()
    val buttonData1 = ButtonData(messageId, buttonId1, "title 1", 0, ButtonData.ButtonNotClicked)
    val buttonId2 = ButtonId()
    val buttonData2 = ButtonData(messageId, buttonId2, "title 2", 1, ButtonData.ButtonWaiting)
    val confirmation = Map(messageId -> Option(buttonId2))

    val ids = Seq((messageId, buttonId1), (messageId, buttonId2))

    // the tested method does not return anything so instead we check if the button is modified in the storage
    (buttons.findByMessage _).expects(messageId).atLeastOnce().returning(Future.successful(Seq(buttonData1, buttonData2)))
    (buttons.updateAll2 _).expects(ids, *).atLeastOnce().returning(Future.successful(Nil))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)

    result(updater.updateButtonConfirmations(confirmation))
  }

  scenario("Confirm no button action") {
    val messageId = MessageId()
    val buttonId1 = ButtonId()
    val buttonData1 = ButtonData(messageId, buttonId1, "title 1", 0, ButtonData.ButtonNotClicked)
    val buttonId2 = ButtonId()
    val buttonData2 = ButtonData(messageId, buttonId2, "title 2", 1, ButtonData.ButtonWaiting)
    val confirmation = Map(messageId -> None)

    val ids = Seq((messageId, buttonId1), (messageId, buttonId2))

    // the tested method does not return anything so instead we check if the button is modified in the storage
    (buttons.findByMessage _).expects(messageId).atLeastOnce().returning(Future.successful(Seq(buttonData1, buttonData2)))
    (buttons.updateAll2 _).expects(ids, *).atLeastOnce().returning(Future.successful(Nil))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)

    result(updater.updateButtonConfirmations(confirmation))
  }

  scenario(
    """Given self deleting messages are disabled by the team,
      | and a conversation has global and local expiration settings
      |When adding new local message to a conversation with a specified expiration parameter
      |Then no expiration should be used""".stripMargin){

    val conversationId = ConvId("ABC")
    val conversationData = ConversationData(
      globalEphemeral = Some(FiniteDuration(30, "s")),
      localEphemeral = Some(FiniteDuration(10, "s")))
    val message = MessageData(convId = conversationId)
    userPrefs(UserPreferences.AreSelfDeletingMessagesEnabled) := false
    userPrefs(UserPreferences.SelfDeletingMessagesEnforcedTimeout) := 0

    (convsStorage.get _).expects(conversationId).once().returning(Future.successful(Some(conversationData)))
    (storage.getLastMessage _).expects(conversationId).once().returning(Future.successful(Some(message)))

    (storage.addMessage _).expects(where{ message: MessageData =>
      message.ephemeral.isEmpty
    }).once().returning(Future.successful(message))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)
    result(updater.addLocalMessage(message, exp = Some(Some(FiniteDuration(20, "s")))))
  }

  scenario(
    """Given self deleting messages are enforced by the team,
      | and a conversation has global and local expiration settings
      |When adding new local message to a conversation with a specified expiration parameter
      |Then the team enforced expiration should be used""".stripMargin){
    val conversationId = ConvId("ABC")
    val conversationData = ConversationData(
      globalEphemeral = Some(FiniteDuration(10, "s")),
      localEphemeral = Some(FiniteDuration(20, "s")))
    val message = MessageData(convId = conversationId)
    userPrefs(UserPreferences.AreSelfDeletingMessagesEnabled) := true
    userPrefs(UserPreferences.SelfDeletingMessagesEnforcedTimeout) := 60

    (convsStorage.get _).expects(conversationId).once().returning(Future.successful(Some(conversationData)))
    (storage.getLastMessage _).expects(conversationId).once().returning(Future.successful(Some(message)))

    (storage.addMessage _).expects(where{ message: MessageData =>
      message.ephemeral.contains(FiniteDuration(60, "s"))
    }).once().returning(Future.successful(message))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)
    result(updater.addLocalMessage(message, exp = Some(Some(FiniteDuration(10, "s")))))
  }

  scenario(
    """Given self deleting messages are enabled by the team,
      | and a conversation has global and local expiration settings
      |When adding new local message to a conversation with a specified expiration parameter
      |Then the conversation timer expiration should be used""".stripMargin){
    val conversationId = ConvId("ABC")
    val conversationData = ConversationData(
      globalEphemeral = Some(FiniteDuration(30, "s")),
      localEphemeral = Some(FiniteDuration(10, "s")))
    val message = MessageData(convId = conversationId)
    userPrefs(UserPreferences.AreSelfDeletingMessagesEnabled) := true
    userPrefs(UserPreferences.SelfDeletingMessagesEnforcedTimeout) := 0

    (convsStorage.get _).expects(conversationId).once().returning(Future.successful(Some(conversationData)))
    (storage.getLastMessage _).expects(conversationId).once().returning(Future.successful(Some(message)))

    (storage.addMessage _).expects(where{ message: MessageData =>
      message.ephemeral.contains(FiniteDuration(30, "s"))
    }).once().returning(Future.successful(message))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)
    result(updater.addLocalMessage(message, exp = Some(Some(FiniteDuration(20, "s")))))
  }

  scenario(
    """Given self deleting messages are enabled by the team,
      | and a conversation has only a local expiration setting
      |When adding new local message to a conversation with a specified expiration parameter
      |Then the specified parameter expiration should be used""".stripMargin){
    val conversationId = ConvId("ABC")
    val conversationData = ConversationData(
      globalEphemeral = None,
      localEphemeral = Some(FiniteDuration(10, "s")))
    val message = MessageData(convId = conversationId)
    userPrefs(UserPreferences.AreSelfDeletingMessagesEnabled) := true
    userPrefs(UserPreferences.SelfDeletingMessagesEnforcedTimeout) := 0

    (convsStorage.get _).expects(conversationId).once().returning(Future.successful(Some(conversationData)))
    (storage.getLastMessage _).expects(conversationId).once().returning(Future.successful(Some(message)))

    (storage.addMessage _).expects(where{ message: MessageData =>
      message.ephemeral.contains(FiniteDuration(20, "s"))
    }).once().returning(Future.successful(message))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)
    result(updater.addLocalMessage(message, exp = Some(Some(FiniteDuration(20, "s")))))
  }

  scenario(
    """Given self deleting messages are enabled by the team,
      | and a conversation has only a local expiration setting
      |When adding new local message to a conversation with NO specified expiration parameter
      |Then the local expiration setting should be used""".stripMargin){
    val conversationId = ConvId("ABC")
    val conversationData = ConversationData(
      globalEphemeral = None,
      localEphemeral = Some(FiniteDuration(10, "s")))
    val message = MessageData(convId = conversationId)
    userPrefs(UserPreferences.AreSelfDeletingMessagesEnabled) := true
    userPrefs(UserPreferences.SelfDeletingMessagesEnforcedTimeout) := 0

    (convsStorage.get _).expects(conversationId).once().returning(Future.successful(Some(conversationData)))
    (storage.getLastMessage _).expects(conversationId).once().returning(Future.successful(Some(message)))

    (storage.addMessage _).expects(where{ message: MessageData =>
      message.ephemeral.contains(FiniteDuration(10, "s"))
    }).once().returning(Future.successful(message))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)
    result(updater.addLocalMessage(message, exp = None))
  }

  scenario(
    """Given self deleting messages are enabled by the team,
      | And the conversation has no expiration settings
      |When adding new local message to a conversation without an expiration parameter
      |Then no expiration should be used""".stripMargin){
    val conversationId = ConvId("ABC")
    val conversationData = ConversationData(
      globalEphemeral = None,
      localEphemeral = None)
    val message = MessageData(convId = conversationId)
    userPrefs(UserPreferences.AreSelfDeletingMessagesEnabled) := true
    userPrefs(UserPreferences.SelfDeletingMessagesEnforcedTimeout) := 0

    (convsStorage.get _).expects(conversationId).once().returning(Future.successful(Some(conversationData)))
    (storage.getLastMessage _).expects(conversationId).once().returning(Future.successful(Some(message)))

    (storage.addMessage _).expects(where{ message: MessageData =>
      message.ephemeral.isEmpty
    }).once().returning(Future.successful(message))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs, userPrefs)
    result(updater.addLocalMessage(message, exp = None))
  }
}
