package com.waz.zclient.convExport

import android.content.Context
import android.net.Uri
import com.waz.content.ConversationStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, RemoteInstant}
import com.waz.service.ZMessaging
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.{Injectable, Injector}
import com.wire.signals.{EventContext, EventStream, Signal, SourceStream}

import io.reactivex.subjects.BehaviorSubject

class ExportController(implicit injector: Injector, context: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {

  val zms: Signal[ZMessaging] = inject[Signal[ZMessaging]]
  val convController: ConversationController = inject[ConversationController]
  lazy val convStorage: ConversationStorage = zms.currentValue.get.convsStorage
  val usersController: UsersController = inject[UsersController]
  val onShowExport: SourceStream[Option[String]] = EventStream[Option[String]]()

  val currentExport: BehaviorSubject[Option[ExportProgress]] = BehaviorSubject.createDefault(None)
  val exportFile: BehaviorSubject[Option[Uri]] = BehaviorSubject.createDefault(None)
  var timeFrom: Option[RemoteInstant] = None
  var timeTo: Option[RemoteInstant] = None
  var exportFiles = true
  var exportProfilePictures = true
  var includeHtml = true
  var cancelExport = false
  var exportConvIds: Option[IndexedSeq[ConvId]] = None

  def export() : Unit = {
    if(exportConvIds.isEmpty){
      exportConvIds=Some(IndexedSeq(convController.getCurrentConvId))
    }
    currentExport.synchronized{
      new Thread(new Runnable {
        override def run(): Unit = {
          if(exportConvIds.nonEmpty){
            val newExport = new ExportConverter(ExportController.this)
            currentExport.onNext(Some(newExport.getExportProgress))
            try{
              newExport.export(exportConvIds.get)
            }catch{
              case e: Exception => e.printStackTrace()
            }finally{
              currentExport.onNext(None)
              cancelExport = false
            }
          }
        }
      }).start()
    }
  }
}

object ExportController {

}
