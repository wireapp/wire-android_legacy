package com.waz.service.otr

import com.waz.model.{ConvId, NotificationData, UserId}

import scala.concurrent.Future

/**
 * A trait representing some controller responsible for displaying notifications in the UI. It is expected that this
 * controller is a global singleton
 */
trait NotificationUiController {
  /**
   * A call to the UI telling it that it has notifications to display. This needs to be a future so that we can wait
   * for the displaying of notifications before finishing the event processing pipeline. Upon completion of the future,
   * we can also mark these notifications as displayed.
   * @return a Future that should enclose the display of notifications to the UI
   */
  def showNotifications(accountId: UserId, ns: Set[NotificationData]): Future[Unit]

  def cancelNotifications(accountId: UserId, convId: ConvId): Unit

  def cancelNotifications(accountId: UserId): Unit
}
