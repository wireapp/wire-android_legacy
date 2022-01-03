package com.waz.service.otr

import com.waz.content.{ConversationStorage, GlobalPreferences}
import com.waz.content.GlobalPreferences.WsForegroundKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, Event, RConvEvent, UserId}
import com.waz.service.conversation.SelectedConversationService
import com.waz.service.{AccountsService, EventScheduler}
import com.waz.service.EventScheduler.Stage
import com.wire.signals.EventContext
import com.waz.service.AccountsService.InForeground

import scala.concurrent.{ExecutionContext, Future}

trait WsNotificationService {
  def eventsProcessingStage: Stage.Atomic
}

final class WsNotificationServiceImpl(selfId:              UserId,
                                      accounts:            AccountsService,
                                      parser:              NotificationParser,
                                      prefs:               GlobalPreferences,
                                      controller:          NotificationUiController,
                                      selectedConvService: SelectedConversationService,
                                      convsStorage:        ConversationStorage)
                                     (implicit ec: ExecutionContext, eventContext: EventContext)
  extends WsNotificationService with DerivedLogTag {

  override val eventsProcessingStage: Stage.Atomic = EventScheduler.Stage[Event] {
    (_, events) => process(events)
  }

  private def process(events: Seq[Event]): Future[Unit] =
    for {
      isWsAlwaysOn    <- prefs(WsForegroundKey).apply()
      isAccountActive <- accounts.accountState(selfId).head.map(_ == InForeground)
    } yield
      if (isWsAlwaysOn || isAccountActive) {
        if (!isAccountActive)
          showAll(events)
        else
          showFromOtherConvs(events)
      }

  private def showAll(events: Seq[Event]) =
    parser.parse(events).flatMap {
      case nots if nots.nonEmpty => controller.showNotifications(selfId, nots)
      case _                     => Future.successful(())
    }

  private def showFromOtherConvs(events: Seq[Event]) =
    for {
      Some(currentConvId) <- selectedConvService.selectedConversationId.head
      filtered            <- eventsFromOtherConvs(events, currentConvId)
      parsed              <- parser.parse(filtered)
    } yield parsed match {
      case nots if nots.nonEmpty => controller.showNotifications(selfId, nots)
      case _                     => Future.successful(())
    }

  private def eventsFromOtherConvs(events: Seq[Event], currentConvId: ConvId): Future[Seq[Event]] =
    Future
      .sequence(events.collect { case ev: RConvEvent => convsStorage.getByRemoteId(ev.convId).map((ev, _)) })
      .map(_.collect { case (ev, Some(conv)) if conv.id != currentConvId => ev })
}
