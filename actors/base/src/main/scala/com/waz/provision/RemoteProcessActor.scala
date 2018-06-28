/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.provision

import akka.actor.Actor.Receive
import akka.actor._
import android.content.Context
import com.waz.service.BackendConfig
import org.threeten.bp.Instant.now

import scala.concurrent.duration._

class RemoteProcessActor(application: Context,
                         processName: String,
                         coordinator: Option[ActorRef],
                         backend: BackendConfig = BackendConfig.StagingBackend)
  extends FSM[RemoteProcessActor.State, Option[ActorRef]] with ActorLogging {

  import ActorMessage._
  import RemoteProcessActor._

  //Using a large value so that the test processes will always timeout first, and not the remotes
  implicit val defaultTimeout = 5.minutes
  implicit val execContext = context.dispatcher.prepare()

  lazy val multicast = new Multicast(context.system)

  startWith(Waiting, None)

  onTransition {
    case (prev, next) => println(s"transition: $prev $next")
  }

  coordinator.fold {
    self.tell(ReceiveTimeout, self)
  } { coord =>
    self.tell(ActorIdentity(0, Some(coord)), coord)
  }

  when(Waiting) {
    case Event(ReceiveTimeout, _) =>
      multicast.send(self, processName)
      context.system.scheduler.scheduleOnce(1.second, self, ReceiveTimeout)
      stay()

    case Event(ActorIdentity(_, _), _) =>
      context.watch(sender())
      goto(Active) using Some(sender()) replying RegisterProcess(processName)
  }

  when(Active) {
    case Event(Terminated(actor), Some(coord)) if actor == coord => stop()

    case Event(ReleaseProcess, _) => stop()

    case Event(Echo(msg, _), _) =>
      stay() replying Echo(msg, processName)

    case Event(SpawnRemoteDevice(_, deviceName), _) =>
      stay() replying spawnDevice(deviceName)
  }

  def spawnDevice(deviceName: String): ActorRef =
    context.actorOf(DeviceActor.props(deviceName, application, backend), deviceName)
}

object RemoteProcessActor {
  def props(context: Context,
            processName: String,
            mainCoordinatorRef: Option[ActorRef],
            backend: BackendConfig = BackendConfig.StagingBackend) =
    Props(new RemoteProcessActor(context, processName, mainCoordinatorRef, backend))

  trait State
  case object Waiting extends State
  case object Active extends State
}

object LoggedReceive {
  def apply(handler: Receive): Receive = {
    case msg =>
      println(s"$now - Received message: $msg")
      handler(msg)
  }
}

class DeserializeActorRefExtensionImpl(extended: ExtendedActorSystem) extends Extension {
  def deserialize(serialized: String) = extended.provider.resolveActorRef(serialized)
}

object DeserializeActorRefExtension extends ExtensionId[DeserializeActorRefExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem) = new DeserializeActorRefExtensionImpl(system)
  override def lookup() = DeserializeActorRefExtension
}
