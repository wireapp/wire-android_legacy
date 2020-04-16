package com.waz.model

import com.waz.db.Dao2
import com.waz.utils.Identifiable
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.model.ButtonData._

case class ButtonData(messageId: MessageId,
                      buttonId:  ButtonId,
                      title:     String,
                      ordinal:   Int,
                      state:     ButtonState = ButtonNotClicked) extends Identifiable[ButtonDataDaoId]{
  override def id: ButtonDataDaoId = (messageId, buttonId)
}

object ButtonData {
  type ButtonDataDaoId = (MessageId, ButtonId)

  sealed trait ButtonState { val id: Int }
  case object  ButtonError      extends ButtonState { override val id: Int = 0 }
  case object  ButtonNotClicked extends ButtonState { override val id: Int = 1 }
  case object  ButtonWaiting    extends ButtonState { override val id: Int = 2 }
  case object  ButtonConfirmed  extends ButtonState { override val id: Int = 3 }

  def buttonState(id: Int) =
   List[ButtonState](ButtonError, ButtonNotClicked, ButtonWaiting, ButtonConfirmed).find(_.id == id).head

  import com.waz.db.Col._
  implicit object ButtonDataDao extends Dao2[ButtonData, MessageId, ButtonId] {
    val Message = id[MessageId]('message_id).apply(_.messageId)
    val Button  = id[ButtonId]('button_id).apply(_.buttonId)
    val Title   = text('title).apply(_.title)
    val Ordinal = int('ordinal).apply(_.ordinal)
    val StateId = int('state).apply(_.state.id)

    override def onCreate(db: DB): Unit = {
      println("ButtonData.onCreate called - we do nothing, the table will be created in Kotlin")
    }

    override val idCol = (Message, Button)

    override val table = Table("Buttons", Message, Button, Title, Ordinal, StateId)

    override def apply(implicit cursor: DBCursor): ButtonData = ButtonData(Message, Button, Title, Ordinal, buttonState(StateId))

    def findForMessage(id: MessageId)(implicit db: DB) = iterating(find(Message, id))
  }

}
