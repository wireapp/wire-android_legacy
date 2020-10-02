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
package com.waz.service

import java.io._
import java.util.concurrent.CountDownLatch

import android.content.Context
import com.waz.cache.{CacheService, Expiration}
import com.waz.content.GlobalPreferences.PushToken
import com.waz.content.WireContentProvider.CacheUri
import com.waz.content.{AccountStorage, GlobalPreferences}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.log.{BufferedLogOutput, InternalLog}
import com.waz.model.{Mime, UserId}
import com.wire.signals.SerialDispatchQueue
import com.waz.threading.Threading
import com.waz.utils.wrappers.URI
import com.waz.utils.{IoUtils, RichFuture}
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

trait ReportingService {
  import ReportingService._
  private implicit val dispatcher = SerialDispatchQueue(name = "ReportingService")
  private[service] var reporters = Seq.empty[Reporter]

  def addStateReporter(report: PrintWriter => Future[Unit])(implicit tag: LogTag): Unit = Future {
    reporters = reporters :+ Reporter(tag.value, report)
  }

  private[service] def generateStateReport(writer: PrintWriter) =
    Future(reporters).flatMap { rs =>
      RichFuture.traverseSequential(rs)(_.apply(writer))
    }.map(_ => {})
}

object ReportingService {

  case class Reporter(name: String, report: PrintWriter => Future[Unit]) {

    def apply(writer: PrintWriter): Future[Unit] = {
      writer.println(s"\n###### $name:")
      report(writer)
    }
  }
}

class ZmsReportingService(user: UserId, global: ReportingService) extends ReportingService {
  private implicit val dispatcher = SerialDispatchQueue(name = "ZmsReportingService")

  global.addStateReporter(generateStateReport)(LogTag(s"ZMessaging[$user]"))
}

class GlobalReportingService(context: Context, cache: CacheService, metadata: MetaDataService, storage: AccountStorage, prefs: GlobalPreferences)
  extends ReportingService
    with DerivedLogTag {

  import ReportingService._
  import Threading.Implicits.Background

  def generateReport(): Future[URI] =
    cache.createForFile(mime = Mime("text/txt"), name = Some("wire_debug_report.txt"), cacheLocation = Some(cache.intCacheDir))(Expiration.in(12.hours)).flatMap { entry =>
      @SuppressWarnings(Array("deprecation"))
      lazy val writer = new PrintWriter(new OutputStreamWriter(entry.outputStream))

      val rs: Seq[Reporter] =
        Seq(VersionReporter) ++ Seq(FcmStatsReporter) ++ (if (metadata.internalBuild) Seq(PushRegistrationReporter, ZUsersReporter) ++ reporters else Seq.empty) ++ Seq(LogCatReporter, InternalLogReporter)

      RichFuture.traverseSequential(rs)(_.apply(writer))
        .map(_ => CacheUri(entry.data, context))
        .andThen {
          case _ => writer.close()
        }
    }

  val FcmStatsReporter = Reporter("FCMStats", { writer =>
    for {
      acc <- ZMessaging.accountsService
      z <- acc.activeZms.collect { case Some(zms) => zms}.head
      stats <- z.fcmNotStatsService.getFormattedStats
    } yield {
      writer.print(stats)
    }
  })

  val VersionReporter = Reporter("Wire", { writer =>
    import android.os.Build._
    Future.successful {
      writer.println(s"time of log: ${Instant.now}")
      writer.println(s"package: ${ZMessaging.context.getPackageName}")
      writer.println(s"app version: ${metadata.appVersion}")
      writer.println(s"device: $MANUFACTURER $PRODUCT | $MODEL | $BRAND | $ID")
      writer.println(s"version: ${VERSION.RELEASE} | ${VERSION.CODENAME}")
    }
  })

  val ZUsersReporter = Reporter("ZUsers", { writer =>
    val current = ZMessaging.currentAccounts.activeAccount.currentValue.flatten
    writer.println(l"current: $current".buildMessageSafe)
    storage.list() map { all =>
      all.filter(!current.contains(_)).foreach { u =>
        writer.println(l"$u".buildMessageSafe)
      }
    }
  })

  val PushRegistrationReporter = Reporter("Push", { writer =>
    prefs.preference(PushToken).apply().map(writer.println )
  })

  val LogCatReporter = Reporter("LogCat", { writer =>

    val latch = new CountDownLatch(2)

    def writeAll(input: InputStream): Unit = try {
      IoUtils.withResource(new BufferedReader(new InputStreamReader(input))) { reader =>
        Iterator.continually(reader.readLine()).takeWhile(_ != null).foreach(writer.println)
      }
    } finally {
      latch.countDown()
    }

    Future {
      import scala.sys.process._
      Process(Seq("logcat", "-d", "-v", "time")).run(new ProcessIO({in => latch.await(); in.close() }, writeAll, writeAll))
      latch.await()
    } (Threading.IO)
  })

  val InternalLogReporter = Reporter("InternalLog", { writer =>
    Future {
      val outputs = InternalLog.getOutputs.flatMap {
        case o: BufferedLogOutput => Some(o)
        case _ => None
      }

      outputs.foreach(_.flush())
      outputs.flatMap(_.getPaths) // paths should be sorted from the oldest to the youngest
        .map(new File(_))
        .filter(_.exists)
        .foreach { file =>
          IoUtils.withResource(new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            reader => Iterator.continually(reader.readLine()).takeWhile(_ != null).foreach(writer.println)
          }
        }
    }
  })
}
