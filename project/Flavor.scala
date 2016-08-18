/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import sbt._
import sbt.Keys._
import android.Keys._
import android.{ApkSigningConfig, PlainSigningConfig}

case class Flavor(
                  versionSuffix: String,
                  appId: String,
                  buildConfig: BuildConfig,
                  manifest: Placeholders,
                  additionalProguardFile: Option[String] = None,
                  signing: ApkSigningConfig = SigningConfig.Internal
                 ) {

  val settings = Seq (
    versionName := Some(version.value + versionSuffix),
    applicationId := appId,
    buildConfigOptions := buildConfig.setting,
    manifestPlaceholders := manifest.setting,
    apkSigningConfig := Some(signing),
    proguardOptions ++= additionalProguardFile.fold(Seq[String]()) { conf => IO.readLines(file("app") / conf) }
  )
}

object Flavor {

  lazy val Custom = Flavor("-custom", "com.waz.zclient", BuildConfig.Dev.copy(useEdgeBackend = false), Placeholders.Dev.copy(applicationLabel = "Wire", applicationIcon = Placeholders.Prod.applicationIcon))
  lazy val Dev = Flavor("-dev", "com.waz.zclient.dev", BuildConfig.Dev, Placeholders.Dev)
  lazy val Candidate = Flavor("-candidate", "com.wire.candidate", BuildConfig.Candidate, Placeholders.Candidate, Some("proguard-android-optimize-wire.txt"))
  lazy val Prod = Flavor("", "com.wire", BuildConfig.Prod, Placeholders.Prod, Some("proguard-android-optimize-wire.txt"), SigningConfig.Release)
  lazy val Internal = Flavor("-internal", "com.wire.internal", BuildConfig.Internal, Placeholders.Internal, Some("proguard-rules-test.txt"))
  lazy val Avs = Flavor("-avs", "com.wire.avs", BuildConfig.Avs, Placeholders.Avs, Some("proguard-rules-test.txt"))
  lazy val QAAvs = Flavor("-qaavs", "com.wire.qaavs", BuildConfig.QAAvs, Placeholders.QAAvs, Some("proguard-rules-test.txt"))
  lazy val Experimental = Flavor("-exp", "com.wire.x", BuildConfig.Experimental, Placeholders.Experimental, Some("proguard-rules-test.txt"))
}

object SigningConfig {

  lazy val Release = PlainSigningConfig(file("app/zclient-release-key.keystore"), System.getenv("KSTOREPWD"), "zclient", Some(System.getenv("KEYPWD")))
  lazy val Internal = PlainSigningConfig(file("app/zclient-test-key.keystore"), "wire22", "wiredebugkey", Some("wire22"))
  lazy val Debug = PlainSigningConfig(file("app/zclient-debug-key.keystore"), "android", "androiddebugkey", Some("android"))
}


case class Placeholders(
                        applicationVmSafeMode: Boolean = true,
                        localyticsGcmSenderId: String = BuildConfig.DevLocalyticsSenderId,
                        sharedUserId: String = "",
                        applicationLabel: String = "Wire Dev",
                        allowBackup: Boolean = true,
                        applicationIcon: String = "@drawable/ic_launcher_wire_dev",
                        useAudioLink: Boolean = false,
                        internalFeatures: Boolean = true,
                        localyticsAppKey: String = "88294647e42fd68a78d6743-9ba13640-414c-11e3-3d6b-00a426b17dd8",
                        hockeyAppKey: String = "1f5602987d1617ab35573c2202438aaf"
                       ) {


  def setting = Map[String, String] (
    "applicationVmSafeMode" -> applicationVmSafeMode.toString,
    "localyticsGcmSenderId" -> s"\\ $localyticsGcmSenderId",
    "sharedUserId"      -> sharedUserId,
    "applicationLabel"  -> applicationLabel,
    "allowBackup"       -> allowBackup.toString,
    "applicationIcon"   -> applicationIcon,
    "use_audio_link"    -> useAudioLink.toString,
    "internal_features" -> internalFeatures.toString,
    "localyticsAppKey"  -> localyticsAppKey,
    "hockeyAppKey"      -> hockeyAppKey
  )
}

object Placeholders {

  lazy val Dev = Placeholders()

  lazy val Candidate = Placeholders(
    applicationLabel = "Wire Candidate",
    applicationIcon = "@drawable/ic_launcher_wire_candidate",
    internalFeatures = false,
    hockeyAppKey = "58bc68003dedd15498f4a772caf23966"
  )

  lazy val Prod = Placeholders(
    applicationLabel = "@string/app_name",
    allowBackup = false,
    internalFeatures = false,
    applicationIcon = "@drawable/ic_launcher_wire",
    sharedUserId = "com.waz.userid",
    localyticsAppKey = "87df1d933d5d95b1a057ba2-4e213c38-756b-11e4-a7de-009c5fda0a25",
    hockeyAppKey = "189d00945f69a08e1605e0b44227fe4d"
  )

  lazy val Internal = Placeholders(
    applicationLabel = "Wire Internal",
    applicationIcon = "@drawable/ic_launcher_wire_internal",
    hockeyAppKey = "b0759466abc3757e339e89a733ad604e"
  )

  lazy val Avs = Placeholders(
    applicationLabel = "Wire AVS",
    internalFeatures = false,
    applicationIcon = "@drawable/ic_launcher_wire_playground",
    hockeyAppKey = "f20c645784bf28e3aec68269566c388f"
  )

  lazy val QAAvs = Placeholders(
    applicationLabel = "Wire QA AVS",
    internalFeatures = false,
    applicationIcon = "@drawable/ic_launcher_wire_playground",
    hockeyAppKey = "76c0b10e19e596ccd2dc021123fbf562"
  )

  lazy val Experimental = Placeholders(
    applicationLabel = "Wire Exp",
    applicationIcon = "@drawable/ic_launcher_wire_playground",
    hockeyAppKey = "85c334bad6f64ba1bed31de65fc3a94a"
  )
}
