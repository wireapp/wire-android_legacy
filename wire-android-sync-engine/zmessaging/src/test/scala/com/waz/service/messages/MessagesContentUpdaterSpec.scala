package com.waz.service.messages

import com.waz.content.{ButtonsStorage, ConversationStorage, MessagesStorage, MsgDeletionStorage}
import com.waz.model.{ButtonData, ButtonId, MessageId}
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.TestGlobalPreferences

import scala.concurrent.Future

class MessagesContentUpdaterSpec extends AndroidFreeSpec {

  private lazy val storage      =  mock[MessagesStorage]
  private lazy val convsStorage =  mock[ConversationStorage]
  private lazy val deletions    =  mock[MsgDeletionStorage]
  private lazy val buttons      =  mock[ButtonsStorage]
  private lazy val prefs        =  new TestGlobalPreferences()

  scenario("Confirm a button action") {
    val messageId = MessageId()
    val buttonId = ButtonId()
    val buttonData = ButtonData(messageId, buttonId, "title", 0, ButtonData.ButtonWaiting)
    val confirmation = Map(messageId -> Option(buttonId))

    // the tested method does not return anything so instead we check if the button is modified in the storage
    (buttons.findByMessage _).expects(messageId).atLeastOnce().returning(Future.successful(Seq(buttonData)))
    (buttons.updateAll2 _).expects(Seq((messageId, buttonId)), *).atLeastOnce().returning(Future.successful(Nil))

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs)

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

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs)

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

    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs)

    result(updater.updateButtonConfirmations(confirmation))
  }
}
