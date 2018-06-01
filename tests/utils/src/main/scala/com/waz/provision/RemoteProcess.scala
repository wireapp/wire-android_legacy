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

import akka.actor.ActorDSL._
import akka.actor._
import com.typesafe.config.ConfigFactory
import com.waz.RoboProcess
import com.waz.ShadowLogging._
import com.waz.ZLog._
import com.waz.service.BackendConfig
import com.waz.utils.LoggedTry
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.scalatest.Informer

import scala.reflect.ClassTag
import scala.sys.process.Process

@Config(manifest = "tests/OtrAndroidManifest.xml")
class RemoteProcess extends RoboProcess {
  import RemoteProcess._

  var processName: String = _
  var serializedCoordinatorActorRef: String = _

  override protected def info: Informer = new Informer {
    override def apply(message: String, payload: Option[Any]): Unit = println(message)
  }

  override def run(args: Seq[String]): Unit = {
    setupShadowLogAround(Console.out)

    //instantiate the actor system and the actor
    implicit val actorSystem = ActorSystem.create("RemoteProcessSystem", ConfigFactory.load("actor_remote"))
    val processName = args(1)

    val coordinatorActor = if (args(2).isEmpty) None else LoggedTry(DeserializeActorRefExtension(actorSystem).deserialize(args(2))).toOption

    val backend = BackendConfig.byName(args(3))

    val processActor = actorSystem.actorOf(RemoteProcessActor.props(Robolectric.application, processName, coordinatorActor, backend), processName.replaceAll("\\s+", "_"))

//    Robolectric.getShadowApplication.grantPermissions(Permission.values.map(_.id):_*)

    // watch remote process actor termination and shutdown actor system
    actor(new Act {
      context.watch(processActor)
      become {
        case Terminated(`processActor`) => context.system.shutdown()
      }
    })

    println("Blocking process until actorSystem terminates")

    while (!actorSystem.isTerminated) {
      try {
        Robolectric.runUiThreadTasks()
      } catch {
        case e: Throwable => e.printStackTrace(System.err)
      }
      Thread.sleep(100)
    }

    //Needs to block or else the clean up will occur and the robolectric application becomes null
    actorSystem.awaitTermination()
    println("Remote process shutting down")
    System.exit(0)
  }
}

object RemoteProcess {
  private implicit val tag: LogTag = logTagFor[RemoteProcess]

  def apply[T : ClassTag](args: String*) = {
    val cls = implicitly[ClassTag[T]].runtimeClass
    Process(Seq("java", s"-Djava.library.path=${System.getProperty("java.library.path")}", "-cp", System.getProperty("java.class.path"), cls.getName) ++ args).run()
  }
}
