package com.waz.service.otr

import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.WsForegroundKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Event, UserId}
import com.waz.service.{EventScheduler, UiLifeCycle}
import com.waz.service.EventScheduler.Stage
import com.wire.signals.EventContext

import scala.concurrent.{ExecutionContext, Future}
import com.waz.log.LogSE._

trait WsNotificationService {
  def eventsProcessingStage: Stage.Atomic
}

final class WsNotificationServiceImpl(selfId:      UserId,
                                      parser:      NotificationParser,
                                      prefs:       GlobalPreferences,
                                      lifecycle:   UiLifeCycle,
                                      controller:  NotificationUiController)
                                     (implicit ec: ExecutionContext, eventContext: EventContext)
  extends WsNotificationService with DerivedLogTag {

  override val eventsProcessingStage: Stage.Atomic = EventScheduler.Stage[Event] {
    (_, events) => process(events)
  }

  private val processEvents: Seq[Event] => Future[Unit] = { events =>
    for {
      parsed <- parser.parse(events)
      _      <- if (parsed.nonEmpty)
                  controller.showNotifications(selfId, parsed)
                else
                  Future.successful(())
    } yield ()
  }

  private val doNothing: Seq[Event] => Future[Unit] = { _ => Future.successful(()) }

  private var process: Seq[Event] => Future[Unit] = doNothing

  private val isActive = for {
    wsForeground   <- prefs(WsForegroundKey).signal
    uiInForeground <- lifecycle.uiActive
  } yield wsForeground && !uiInForeground

  isActive.foreach {
    case true  =>
      verbose(l"processing turned on")
      process = processEvents
    case false =>
      verbose(l"processing turned off")
      process = doNothing
  }
}
