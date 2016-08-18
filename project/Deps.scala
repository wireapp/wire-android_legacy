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
import android.Keys.MutableSetting
import sbt._

object Deps {

  lazy val zmsDevVersion = "78-SNAPSHOT"
  // Release version number must be like this X.0(.Y)
  lazy val zmsReleaseVersion = "72.0.260"

  lazy val avsVersion = Option(System.getenv("AVS_VERSION")).getOrElse("2.7.19")
  lazy val avsName = Option(System.getenv("AVS_NAME")).getOrElse("avs")

  val supportLibVersion = "23.4.0"
  val playServicesVersion = "7.8.+"
  val audioVersion = "1.195.0"
  val checkstyleVersion = "6.17"


  def zMessaging(version: String) = "com.wire" % "zmessaging-android" % version
  lazy val avsDep = "com.wearezeta.avs" % avsName % avsVersion

  lazy val autoService = "com.google.auto.service" % "auto-service" % "1.0-rc2"
  lazy val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.4"
  lazy val javaPoet = "com.squareup" % "javapoet" % "1.5.1"
  lazy val javaWriter = "com.squareup" % "javawriter" % "2.5.1"

  lazy val lintapi = "com.android.tools.lint" % "lint-api" % "24.5.0"
  lazy val lintchecks = "com.android.tools.lint" % "lint-checks" % "24.5.0"

  lazy val checkstyleapi = "com.puppycrawl.tools" % "checkstyle" % checkstyleVersion

  lazy val supportv4 = "com.android.support" % "support-v4" % supportLibVersion
  lazy val supportv13 = "com.android.support" % "support-v13" % supportLibVersion
  lazy val supportdesign = "com.android.support" % "design" % supportLibVersion
  lazy val appcompatv7 = "com.android.support" % "appcompat-v7" % supportLibVersion
  lazy val recyclerview = "com.android.support" % "recyclerview-v7" % supportLibVersion
  lazy val preferences = "com.android.support" % "preference-v7" % supportLibVersion
  lazy val multidex = "com.android.support" % "multidex" % "1.0.1"
  lazy val supportannotations = "com.android.support" % "support-annotations" % supportLibVersion
  lazy val cardview = "com.android.support" % "cardview-v7" % supportLibVersion

  lazy val psBase = "com.google.android.gms" % "play-services-base" % playServicesVersion
  lazy val psGcm = "com.google.android.gms" % "play-services-gcm" % playServicesVersion
  lazy val psMaps = "com.google.android.gms" % "play-services-maps" % playServicesVersion
  lazy val psLocation = "com.google.android.gms" % "play-services-location" % playServicesVersion

  lazy val roundedimageview = "com.makeramen" % "roundedimageview" % "2.2.0"
  lazy val timber = "com.jakewharton.timber" % "timber" % "4.1.1"
  lazy val hockey = "net.hockeyapp.android" % "HockeySDK" % "3.7.2"
  lazy val threetenabp = "com.jakewharton.threetenabp" % "threetenabp" % "1.0.3"
  lazy val localytics = "com.localytics.android" % "library" % "3.8.0"
  lazy val nineoldandroids = "com.nineoldandroids" % "library" % "2.4.0"
  lazy val rebound = "com.facebook.rebound" % "rebound" % "0.3.8"
  lazy val supportpreferences = "net.xpece.android" % "support-preference" % "0.8.1"

  lazy val spotifyAuth = "com.wire" % "spotify-auth" % "1.0.0-beta13"
  lazy val spotifyPlayer = "com.wire" % "spotify-player" % "1.0.0-beta13"

  lazy val audioNotifications = "com.wearezeta.avs" % "audio-notifications" % audioVersion

  lazy val junit = "junit" % "junit" % "4.12"
  lazy val easymock = "org.easymock" % "easymock" % "3.3"
  lazy val testRunner = "com.android.support.test" % "runner" % "0.4.1"
  lazy val testRules = "com.android.support.test" % "rules" % "0.4.1"
  lazy val espresso = "com.android.support.test.espresso" % "espresso-core" % "2.2"
  lazy val espressoIntents = "com.android.support.test.espresso" % "espresso-intents" % "2.2"
  lazy val hamcrestCore = "org.hamcrest" % "hamcrest-core" % "1.3"
  lazy val hamcrestLib = "org.hamcrest" % "hamcrest-library" % "1.3"
  lazy val hamcrestIntegration = "org.hamcrest" % "hamcrest-integration" % "1.3"

  lazy val mockitoCore = "org.mockito" % "mockito-core" % "1.10.19"
  lazy val dexmaker = "com.crittercism.dexmaker" % "dexmaker" % "1.4"
  lazy val dexmakerDx = "com.crittercism.dexmaker" % "dexmaker-dx" % "1.4"
  lazy val dexmakerMockito = "com.crittercism.dexmaker" % "dexmaker-mockito" % "1.4"

  lazy val translations = "com.wire" % "wiretranslations" % "1.+"

  lazy val mp4parser = "com.googlecode.mp4parser" % "isoparser" % "1.1.18"
}
