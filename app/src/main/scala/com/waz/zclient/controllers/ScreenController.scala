package com.waz.zclient.controllers

import android.content.Context
import android.view.View
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.profile.ZetaPreferencesActivity
import com.waz.zclient.{Injectable, Injector}

class ScreenController(implicit injector: Injector, context: Context) extends Injectable {

  val conversationController = inject[IConversationScreenController]

  def openOtrDevicePreferences() =
    context.startActivity(ZetaPreferencesActivity.getOtrDevicesPreferencesIntent(context))

  def showParticipants(anchorView: View, showDeviceTabIfSingle: Boolean = false) =
    conversationController.showParticipants(anchorView, showDeviceTabIfSingle)

}
