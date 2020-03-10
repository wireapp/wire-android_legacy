package com.waz.model

import com.waz.db.Dao2
import com.waz.utils.Identifiable
import com.waz.utils.wrappers.{DB, DBCursor}

import com.waz.model.ButtonData._

case class ButtonData(messageId: MessageId,
                      buttonId:  ButtonId,
                      title:     String,
                      ord:     Int,
                      state:     ButtonState = ButtonNotClicked) extends Identifiable[(MessageId, ButtonId)]{
  override def id: ButtonDataDaoId = (messageId, buttonId)

  def copyWithError(error: String): ButtonData = copy(state = ButtonError(error))
}

object ButtonData {
  type ButtonDataDaoId = (MessageId, ButtonId)

  private val ButtonErrorId = 0

  sealed trait ButtonState { val id: Int }
  case class   ButtonError(error: String) extends ButtonState { override val id: Int = ButtonErrorId }
  case object  ButtonNotClicked           extends ButtonState { override val id: Int = 1 }
  case object  ButtonWaiting              extends ButtonState { override val id: Int = 2 }
  case object  ButtonConfirmed            extends ButtonState { override val id: Int = 3 }

  private val validStates =
    List[ButtonState](ButtonNotClicked, ButtonWaiting, ButtonConfirmed)
    .map(state => state.id -> state).toMap

  def buttonState(id: Int, error: String): ButtonState = id match {
    case ButtonErrorId => ButtonError(error)
    case _             => validStates(id)
  }

  import com.waz.db.Col._
  implicit object ButtonDataDao extends Dao2[ButtonData, MessageId, ButtonId] {
    val Message = id[MessageId]('message_id).apply(_.messageId)
    val Button  = id[ButtonId]('button_id).apply(_.buttonId)
    val Title   = text('title).apply(_.title)
    val Ord   = int('ord).apply(_.ord)
    val StateId = int('state).apply(_.state.id)
    val Error   = text('error).apply(_.state match {
      case ButtonError(error) => error
      case _ => ""
    })

    override val idCol = (Message, Button)

    override val table = Table("Buttons", Message, Button, Title, Ord, StateId, Error)

    override def apply(implicit cursor: DBCursor): ButtonData = ButtonData(Message, Button, Title, Ord, buttonState(StateId, Error))

    def findForMessage(id: MessageId)(implicit db: DB) = iterating(find(Message, id))
  }

}