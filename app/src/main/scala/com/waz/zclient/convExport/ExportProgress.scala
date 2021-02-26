package com.waz.zclient.convExport

import com.waz.zclient.{R, WireApplication}
import scala.language.implicitConversions

class ExportProgress(exportConverter: ExportConverter) {
  var conversationsTotal: Int = 0
  var conversationsDone: Int = 0
  var usersTotal: Int = 0
  var usersDone: Int = 0
  var messagesCurrentConversationTotal: Int = -1
  var messagesCurrentConversationDone: Int = 0
  var assetDownloadTotal: Long = -1
  var assetDownloadDone: Long = -1
  var currentState: ExportProgressState.Value = ExportProgressState.INIT
  def getExportConverter: ExportConverter = exportConverter
}

object ExportProgressState extends Enumeration{
  protected case class Val(desc: String) extends super.Val{
    def getDescription: String = {
      try{
        val resourceField = classOf[R.string].getDeclaredField(desc)
        val resourceId = resourceField.getInt(resourceField)
        WireApplication.APP_INSTANCE.getApplicationContext.getString(resourceId)
      }catch{
        case e: Exception => desc
      }
    }
  }
  implicit def valueToExportProgressStateVal(x: Value): Val = x.asInstanceOf[Val]
  val INIT: Val           = Val("init_export")
  val STARTED: Val        = Val("start_export")
  val CONVERSATIONS: Val  = Val("conversation_export")
  val USERS: Val          = Val("user_export")
  val XML: Val            = Val("xml_export")
  val HTML: Val           = Val("html_export")
  val DONE: Val           = Val("done_export")
  val CANCELED: Val       = Val("canceled_export")
}
