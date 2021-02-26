package com.waz.zclient.`export`

import android.content.Context
import android.net.Uri
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.RemoteInstant
import com.waz.service.ZMessaging
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.{Injectable, Injector}
import com.wire.signals.{EventContext, EventStream, Signal, SourceStream}

import scala.concurrent.ExecutionContext.Implicits.global

class ExportController(implicit injector: Injector, context: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {

  val zms: Signal[ZMessaging] = inject[Signal[ZMessaging]]
  val convController: ConversationController = inject[ConversationController]
  val usersController: UsersController = inject[UsersController]
  val onShowExport: SourceStream[Option[Integer]] = EventStream[Option[Integer]]()

  var exportFile: Option[Uri] = None
  var timeFrom: Option[RemoteInstant] = None
  var timeTo: Option[RemoteInstant] = None
  var exportFiles = true
  var includeHtml = true

  def export(callbackFinished: ()=>Unit) : Unit = {
    convController.currentConvId.future.onSuccess{
      case id => {
        new ExportConverter(ExportController.this).export(Seq(id))
        callbackFinished()
      }
    }
  }
}

object ExportController {

}
