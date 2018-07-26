import java.io.File

import android.Keys._
import bintray.BintrayPlugin.autoImport._
import de.heikoseeberger.sbtheader.license.License
import de.heikoseeberger.sbtheader.HeaderPattern._
import sbt.Tests.{Group, SubProcess}
import sbt._
import sbt.Keys._

import scala.util.Random
import scala.util.matching.Regex

object SharedSettings {

  case class EmailTestUser(email: String, password: String)
  case class InternalBackendPasswords(staging: String)

  val avsVersion = "3.4.100"
  val audioVersion = "1.195.0"
  val RobolectricVersion = "5.0.0_r2-robolectric-1"
  val supportLibVersion = "26.0.1"
  val cryptoboxVersion = "1.0.0"
  val circeVersion = "0.9.3"

  object Deps {
    lazy val avs = "com.wire" % "avs" % avsVersion
    lazy val avsAudio = "com.wire.avs" % "audio-notifications" % audioVersion
    lazy val cryptobox = "com.wire" % "cryptobox-android" % cryptoboxVersion
    lazy val genericMessage = "com.wire" % "generic-message-proto" % "1.21.6"
    lazy val backendApi = "com.wire" % "backend-api-proto" % "1.1"
    lazy val supportV4 = "com.android.support" % "support-v4" % supportLibVersion
    lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.12.5" % Test
    lazy val wireMock = "com.github.tomakehurst" % "wiremock" % "1.53" % Test // current versions requires Java 8...
    lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
    lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
    lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  }

  lazy val RegressionTest = config("regression") extend Test
  lazy val Native = config("native").hide

  lazy val androidSdkDir = settingKey[File]("Android sdk dir from ANDROID_HOME")
  lazy val generateZmsVersion = taskKey[Seq[File]]("generate ZmsVersion.java")
  lazy val generateDebugMode = taskKey[Seq[File]]("generate DebugMode.scala")
  lazy val generateCredentials = taskKey[Seq[File]]("generate InternalCredentials.scala")
  lazy val actorsResources = taskKey[File]("Creates resources zip for remote actor")
  lazy val nativeLibs = taskKey[Classpath]("directories containing native libs for osx and linux build")
  lazy val timespanScaleFactor = settingKey[Double]("scale (some) timespans in tests")
  lazy val emailTestUser = settingKey[EmailTestUser]("email address and password for our registration/login test user")
  lazy val internalBackend = settingKey[InternalBackendPasswords]("passwords for the internal backend interfaces")

  def path(files: Seq[File]) = files.mkString(File.pathSeparator)
  def libraryPathOption(files: Classpath*) = s"-Djava.library.path=${path(files.flatMap(_.map(_.data)).distinct)}"

  def groupByPackage(tests: Seq[TestDefinition], jvmOptions: Seq[String]) =
    tests.groupBy(t => t.name.substring(0, t.name.lastIndexOf('.'))).map {
      case (pkg, ts) => new Group(pkg, ts, SubProcess(ForkOptions(runJVMOptions = jvmOptions ++ Seq("-Xdebug", s"-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=${6000 + Random.nextInt % 1000}"))))
    } .toSeq

  lazy val nativeLibsSettings = Seq(
    nativeLibs in Global := {
      val target = crossTarget.value / "native-libs"
      target.mkdirs()
      val archives = update.value.select(configurationFilter(Native.name))
      archives .foreach { archive =>
        Seq("tar", "xzf", archive.absolutePath, "-C", target.absolutePath, "lib", "libs/osx", "libs/x86").!
      }
      target.listFiles().filter(_.isFile).foreach(_.delete())
      IO.move((target ** "lib*.*").pair(f => Some(target / f.getName)))

      val jni = collectJni.value.flatMap(d => Seq(d / "x86", d / "osx"))

      (target +: jni).classpath
    }
  )

  lazy val testSettings = nativeLibsSettings ++ Seq(
    fork := true,
    crossPaths := false,
    platformTarget in Android := "android-24",

    javaOptions ++= Seq("-Xmx3072M", "-XX:MaxPermSize=3072M", "-XX:+CMSClassUnloadingEnabled", "-Djava.net.preferIPv4Stack=true"),
    testGrouping in Test := { groupByPackage( (definedTests in Test).value, (javaOptions in Test).value ) },
    javaOptions in Test ++= Seq(libraryPathOption(nativeLibs.value)),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-F", (timespanScaleFactor in Test).value.toString),
    testOptions in Test <+= (target in Test) map {
      t => Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", t + "/test-reports")
    },

    unmanagedResourceDirectories in Test += baseDirectory.value.getParentFile / "resources",

    ivyConfigurations += Native,
    testFrameworks := Seq(TestFrameworks.ScalaTest),

    sourceGenerators in Test += (rGenerator in Android).taskValue,
    timespanScaleFactor in Test := 1.0,

    libraryDependencies ++= Seq(
      "org.apache.httpcomponents" % "httpclient" % "4.5.1", // to override version included in robolectric
      "junit" % "junit" % "4.8.2", //to override version included in robolectric
      "com.android.support" % "support-v4" % supportLibVersion,
      "com.google.android.gms" % "play-services-base" % "11.0.0" % Provided exclude("com.android.support", "support-v4"),
      "com.google.firebase" % "firebase-messaging" % "11.0.0" % Provided,
      Deps.avs,
      Deps.cryptobox,
      "com.wire" % "avs-native" % avsVersion % Native,
      "com.wire.cryptobox" % "cryptobox-jni" % cryptoboxVersion % Native classifier "darwin-x86_64",
      "com.wire.cryptobox" % "cryptobox-jni" % cryptoboxVersion % Native classifier "linux-x86_64",
      Deps.scalaCheck,
      Deps.wireMock
    )
  )

  lazy val integrationCredentials = Seq(
    sourceGenerators in Test += generateCredentials.taskValue,
    generateCredentials := {
      val file = (sourceManaged in IntegrationTest).value / "com" / "waz" / "build" / "InternalCredentials.scala"

      val content =
        """package com.waz.build
          |import com.waz.service._
          |object InternalCredentials {
          |  def backend(backend: BackendConfig) = ("wire-staging", %s)
          |  def email = ("%s", "%s")
          |}
        """.stripMargin.format("\"\"\"" + (internalBackend in Test).value.staging + "\"\"\"", (emailTestUser in Test).value.email, (emailTestUser in Test).value.password)
      IO.write(file, content)
      Seq(file)
    }
  )

  lazy val publishSettings = Seq(
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in packageDoc := false,
    publishArtifact in Test := false,
    publishMavenStyle := true,
    bintrayOrganization := Some("wire-android"),
    bintrayRepository := {
      if (sys.env.get("JOB_NAME").exists(_.endsWith("-master"))) "releases" else "snapshots"
    }
  )

  object GPLv3 extends License {
    override def apply(year: String, copyrightOwner: String, commentStyle: String = "*"): (Regex, String) = { (
      cStyleBlockComment,
      s"""|/*
          | * Wire
          | * Copyright (C) $year $copyrightOwner
          | *
          | * This program is free software: you can redistribute it and/or modify
          | * it under the terms of the GNU General Public License as published by
          | * the Free Software Foundation, either version 3 of the License, or
          | * (at your option) any later version.
          | *
          | * This program is distributed in the hope that it will be useful,
          | * but WITHOUT ANY WARRANTY; without even the implied warranty of
          | * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
          | * GNU General Public License for more details.
          | *
          | * You should have received a copy of the GNU General Public License
          | * along with this program. If not, see <http://www.gnu.org/licenses/>.
          | */
          |""".stripMargin
      )
    }
  }
}
