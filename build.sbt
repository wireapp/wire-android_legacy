import java.io.File

import android.Keys._
import com.android.tools.lint.checks.ApiDetector
import sbt.Keys.{libraryDependencies, _}
import sbt.Tests.{Group, SubProcess}
import sbt._

import scala.util.Random

val MajorVersion = "134"
val MinorVersion = "1" // hotfix release

version in ThisBuild := {
  val jobName = sys.env.get("JOB_NAME")
  val isPR = sys.env.get("PR").fold(false)(_.toBoolean)
  val buildNumber = sys.env.get("BUILD_NUMBER")
  val master = jobName.exists(_.endsWith("-master"))
  val buildNumberString = buildNumber.fold("-SNAPSHOT")("." + _)
  if (master) MajorVersion + "." + MinorVersion + buildNumberString
  else if (isPR) MajorVersion + buildNumber.fold("-PR")("." + _ + "-PR")
  else MajorVersion + buildNumberString
}

crossPaths in ThisBuild := false
organization in ThisBuild := "com.wire"

scalaVersion in ThisBuild := "2.11.12"

javacOptions in ThisBuild ++= Seq("-source", "1.7", "-target", "1.7", "-encoding", "UTF-8")
scalacOptions in ThisBuild ++= Seq(
  "-feature", "-target:jvm-1.7", "-Xfuture", //"-Xfatal-warnings",
  "-deprecation", "-Yinline-warnings", "-encoding", "UTF-8", "-Xmax-classfile-name", "128")

platformTarget in ThisBuild := "android-24"

licenses in ThisBuild += ("GPL-3.0", url("https://opensource.org/licenses/GPL-3.0"))

resolvers in ThisBuild ++= Seq(
  Resolver.mavenLocal,
  Resolver.jcenterRepo,
  Resolver.bintrayRepo("wire-android", "releases"),
  Resolver.bintrayRepo("wire-android", "snapshots"),
  Resolver.bintrayRepo("wire-android", "third-party"),
  "Maven central 1" at "http://repo1.maven.org/maven2",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Google Maven repo" at "https://maven.google.com"
)

lazy val Native = config("native").hide

val RobolectricVersion = "5.0.0_r2-robolectric-1"
val circeVersion = "0.9.3"

lazy val root = Project("zmessaging-android", file("."))
  .aggregate(macrosupport, zmessaging)
  .settings(
    //TODO these changes don't publish macrosupport, which can lead to some subtle issues if you're not aware of that
    //We should think of a better way to do it
    aggregate in publish      := false,
    aggregate in publishLocal := false,
    aggregate in publishM2    := false,
    publish := { (publish in zmessaging).value },
    publishLocal := { (publishLocal in zmessaging).value },
    publishM2 := { (publishM2 in zmessaging).value },
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "0.6"),
      "com.github.ghik" %% "silencer-lib" % "0.6"
    )
  )

lazy val zmessaging = project
  .enablePlugins(AutomateHeaderPlugin).settings(licenseHeaders)
  .settings(publishSettings: _*)
  .dependsOn(macrosupport)
  .enablePlugins(AndroidLib)
  .settings(
    name := "zmessaging-android",
    fork := true,
    crossPaths := false,
    platformTarget := "android-24",
    lintDetectors := Seq(ApiDetector.UNSUPPORTED),
    lintStrict := true,
    libraryProject := true,
    typedResources := false,
    sourceGenerators in Compile += generateZmsVersion.taskValue,
    ndkAbiFilter := Seq("armeabi-v7a", "x86"),
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
    },
    ndkBuild := {
      println("NDK building")
      val jni = ndkBuild.value
      val jniSrc = sourceDirectory.value / "main" / "jni"
      val osx = jni.head / "osx"
      osx.mkdirs()

      s"sh ${jniSrc.getAbsolutePath}/build_osx.sh".!

      jni
    },
    javaOptions in Test ++= Seq("-Xmx3072M", "-XX:MaxPermSize=3072M", "-XX:+CMSClassUnloadingEnabled", "-Djava.net.preferIPv4Stack=true"),
    testGrouping in Test := { groupByPackage( (definedTests in Test).value, (javaOptions in Test).value ) },
    javaOptions in Test ++= Seq(libraryPathOption(nativeLibs.value)),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-F", (timespanScaleFactor in Test).value.toString),
    testOptions in Test <+= (target in Test) map {
      t => Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", t + "/test-reports")
    },

    unmanagedResourceDirectories in Test += baseDirectory.value.getParentFile / "resources",

    ivyConfigurations += Native,
    testFrameworks := Seq(TestFrameworks.ScalaTest),

    timespanScaleFactor in Test := 1.0,
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" %% "silencer-plugin"       % "0.6"),

      "org.scala-lang.modules"        %% "scala-async"           % "0.9.7",
      "com.squareup.okhttp3"          %  "okhttp"                % "3.9.0",
      "com.googlecode.libphonenumber" %  "libphonenumber"        % "7.1.1", // 7.2.x breaks protobuf
      "com.wire"                      %  "cryptobox-android"     % "1.0.0",
      "com.wire"                      %  "generic-message-proto" % "1.23.0",
      "com.wire"                      %  "backend-api-proto"     % "1.1",
      "io.circe"                      %% "circe-core"            % circeVersion,
      "io.circe"                      %% "circe-generic"         % circeVersion,
      "io.circe"                      %% "circe-parser"          % circeVersion,
      "com.wire"                      %  "icu4j-shrunk"          % "57.1",
      "com.googlecode.mp4parser"      %  "isoparser"             % "1.1.7",
      "com.github.ghik"               %% "silencer-lib"          % "0.6",

      //Provided dependencies
      "com.softwaremill.macwire"      %% "macros"                % "2.2.2"            % Provided,
      "com.google.android.gms"        %  "play-services-base"    % "11.0.0"           % Provided exclude("com.android.support", "support-v4"),
      "com.wire"                      %  "avs"                   % "3.4.100"          % Provided,
      "com.android.support"           %  "support-v4"            % "26.0.1"           % Provided,
      "org.threeten"                  %  "threetenbp"            % "1.3.+"            % Provided,
      "net.java.dev.jna"              %  "jna"                   % "4.4.0"            % Provided,
      "org.robolectric"               %  "android-all"           % RobolectricVersion % Provided,

      //Test dependencies
      "org.scalatest"                 %% "scalatest"             % "3.0.5"            % Test,
      "org.scalamock"                 %% "scalamock"             % "4.1.0"            % Test,
      "org.scalacheck"                %% "scalacheck"            % "1.14.0"           % Test,
      "com.wire"                      %% "robotest"              % "0.7"              % Test exclude("org.scalatest", "scalatest"),
      "org.robolectric"               %  "android-all"           % RobolectricVersion % Test,
      "junit"                         %  "junit"                 % "4.8.2"            % Test, //to override version included in robolectric
      "io.fabric8"                    %  "mockwebserver"         % "0.1.0"            % Test,
      "org.apache.httpcomponents"     %  "httpclient"            % "4.5.3"            % Test
//      "com.wire.cryptobox" % "cryptobox-jni" % cryptoboxVersion % Test % Native classifier "darwin-x86_64",
//      "com.wire.cryptobox" % "cryptobox-jni" % cryptoboxVersion  % Test % Native classifier "linux-x86_64",
    )
  )

lazy val macrosupport = project
  .enablePlugins(AutomateHeaderPlugin).settings(licenseHeaders)
  .settings(publishSettings: _*)
  .settings(
    version := "3.1",
    crossPaths := false,
    exportJars := true,
    name := "zmessaging-android-macrosupport",
    sourceGenerators in Compile += generateDebugMode.taskValue,
    bintrayRepository := "releases",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % (scalaVersion in ThisBuild).value % Provided,
      "org.robolectric" % "android-all" % RobolectricVersion % Provided
    )
  )

generateZmsVersion in zmessaging := {
  val file = (sourceManaged in Compile in zmessaging).value / "com"/ "waz" / "api" / "ZmsVersion.java"
  val content = """package com.waz.api;
                  |
                  |public class ZmsVersion {
                  |   public static final String ZMS_VERSION = "%s";
                  |   public static final int ZMS_MAJOR_VERSION = %s;
                  |   public static final boolean DEBUG = %b;
                  |}
                """.stripMargin.format(version.value, MajorVersion, sys.env.get("BUILD_NUMBER").isEmpty || sys.props.getOrElse("debug", "false").toBoolean)
  IO.write(file, content)
  Seq(file)
}

generateDebugMode in macrosupport := {
  val file = (sourceManaged in Compile in macrosupport).value / "com"/ "waz" / "DebugMode.scala"
  val content = """package com.waz
                  |
                  |import scala.reflect.macros.blackbox.Context
                  |
                  |object DebugMode {
                  |  val isEnabled: Boolean = %b
                  |  def DEBUG(c: Context) = {
                  |    import c.universe._
                  |    Literal(Constant(isEnabled))
                  |  }
                  |}
                """.stripMargin.format(sys.env.get("BUILD_NUMBER").isEmpty || sys.props.getOrElse("debug", "false").toBoolean)
  IO.write(file, content)
  Seq(file)
}

lazy val licenseHeaders = HeaderPlugin.autoImport.headers := Set("scala", "java", "rs") .map { _ -> GPLv3("2016", "Wire Swiss GmbH") } (collection.breakOut)
lazy val androidSdkDir = settingKey[File]("Android sdk dir from ANDROID_HOME")
lazy val generateZmsVersion = taskKey[Seq[File]]("generate ZmsVersion.java")
lazy val generateDebugMode = taskKey[Seq[File]]("generate DebugMode.scala")
lazy val generateCredentials = taskKey[Seq[File]]("generate InternalCredentials.scala")
lazy val actorsResources = taskKey[File]("Creates resources zip for remote actor")
lazy val nativeLibs = taskKey[Classpath]("directories containing native libs for osx and linux build")
lazy val timespanScaleFactor = settingKey[Double]("scale (some) timespans in tests")


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

def groupByPackage(tests: Seq[TestDefinition], jvmOptions: Seq[String]) =
  tests.groupBy(t => t.name.substring(0, t.name.lastIndexOf('.'))).map {
    case (pkg, ts) => new Group(pkg, ts, SubProcess(ForkOptions(runJVMOptions = jvmOptions ++ Seq("-Xdebug", s"-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=${6000 + Random.nextInt % 1000}"))))
  } .toSeq

def path(files: Seq[File]) = files.mkString(File.pathSeparator)
def libraryPathOption(files: Classpath*) = s"-Djava.library.path=${path(files.flatMap(_.map(_.data)).distinct)}"

lazy val fullCoverage = taskKey[Unit]("Runs all tests and generates coverage report of zmessaging")

fullCoverage := {
  (coverageReport in zmessaging).value
}
