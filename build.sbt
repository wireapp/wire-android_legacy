import android.Dependencies.LibraryDependency
import android.Keys._

import scala.collection.mutable

import Deps._

val MajorVersion = "2.7"

lazy val buildNumber = Option(System.getenv("BUILD_NUMBER")).map(_.toInt).getOrElse(99999)

version in Global := s"$MajorVersion.$buildNumber"

zmsVersion in Global := Option(System.getenv("ZMESSAGING_VERSION")).getOrElse(zmsDevVersion)

scalaVersion in Global := "2.11.8"

compileOrder in Global := CompileOrder.Mixed

javacOptions in Global ++= Seq("-source", "1.7", "-target", "1.7")

scalacOptions in Global ++= Seq("-feature", "-target:jvm-1.7", "-Xfuture", "-deprecation", "-Yinline-warnings", "-Ywarn-unused-import", "-encoding", "UTF-8")

resolvers in Global ++= Seq (
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  Resolver.bintrayRepo("wire-android", "releases"),
  Resolver.bintrayRepo("wire-android", "snapshots"),
  Resolver.bintrayRepo("wire-android", "third-party"),
  "android team nexus" at "http://192.168.10.18:8081/nexus/content/groups/public",
  "Maven central 1" at "http://repo1.maven.org/maven2",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Bintray consp1razy" at "http://dl.bintray.com/consp1racy/maven",
  "Localytics" at "http://maven.localytics.com/public"
)

lintDetectors := Seq()//ApiDetector.UNSUPPORTED)
lintStrict := false

val subProjectSettings = android.Plugin.buildAar ++ Seq(
  libraryProject := true,
  platformTarget := "android-23",
  useProguard := false,
  debugIncludesTests := false,
  dexMulti := true,
  dexInputs ~= { di => (di._1, di._2 filterNot (_.getName startsWith "scala-library")) },
  publishArtifact in (Compile, packageBin) := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in packageDoc := false,
  dexMaxHeap := "6144M",
  lintDetectors := Seq(),//ApiDetector.UNSUPPORTED),
  lintStrict := false,
  transitiveAndroidLibs := false
)

lazy val wireCore = project.in(file("wire-core"))
  .settings(androidBuildAar: _ *)
  .settings(subProjectSettings: _ *)
  .settings(
    libraryDependencies ++= Seq(supportv4, appcompatv7, timber, hockey, translations, zMessaging(zmsVersion.value), avsDep)
  )

lazy val wireUi = project.in(file("wire-ui"))
  .androidBuildWith(wireCore)
  .settings(subProjectSettings: _ *)
  .settings(
    libraryDependencies ++= Seq(recyclerview, threetenabp, preferences, supportdesign,
      supportpreferences exclude("net.xpece.android", "support-spinner"),
      rebound,
      roundedimageview,
      "net.xpece.android" % "support-spinner" % "0.8.1")
  )

lazy val app = Project("zclient-app", file("app"))
  .androidBuildWith(wireUi)
  .settings(Flavor.Dev.settings: _*)
	.settings(
    name := "zclient-app",
    versionCode := Some(buildNumber),
    libraryProject := false,
    localProjects := {
      val libs = localProjects.value
      libs.map(l => l.layout.base.getName -> l).toMap.values.toSeq
    },
    platformTarget := "android-23",
    proguardCache := Seq(),
    debugIncludesTests := false,
    useProguard := true,
    useProguardInDebug := true,
    proguardOptions ++= IO.readLines(file("app") / "proguard-rules.txt"),
    proguardConfig ++= IO.readLines(file("app") / "proguard-rules.txt"),
    typedResources := false,
    retrolambdaEnabled := false,
    dexMulti := true,
    publishArtifact in (Compile, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    packagingOptions := {
      val p = packagingOptions.value
      p.copy(excludes = p.excludes ++ Seq("META-INF/DEPENDENCIES", "META-INF/DEPENDENCIES.txt", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/LICENSE", "LICENSE.txt", "META-INF/LICENSE.txt", "META-INF/services/javax.annotation.processing.Processor"))
    },
    dexMainClasses ++= Seq("com.waz.zclient.ZApplication", "com.waz.zclient.MainActivity"),
    proguardScala := useProguard.value,
    dexInputs := {
      val di = dexInputs.value
      if (proguardScala.value) di
      else {
        val names = new mutable.HashSet[String]
        (di._1, di._2 filterNot { f => f.getName.startsWith("scala-library") && names.add(f.getName) })
      }
    },
    apkDebugSigningConfig := SigningConfig.Debug,
    transitiveAndroidLibs := true,
    dexMaxHeap := "6144M",
    minSdkVersion := "17",
    libraryDependencies ++= Seq (multidex, supportannotations, supportdesign, audioNotifications, cardview,
      spotifyAuth, spotifyPlayer, nineoldandroids, localytics, psBase, psGcm, psMaps, psLocation, mp4parser,
      "com.jakewharton.hugo" % "hugo-annotations" % "1.2.1" % "provided",
      "com.wire" % "testutils" % zmsDevVersion % Test,
      "com.geteit" %% "robotest" % "0.7" % Test exclude("org.scalatest", "scalatest"),
      "org.scalatest" %% "scalatest" % "2.2.6" % Test,
      "junit" % "junit" % "4.12" % Test,
      "org.mockito" % "mockito-all" % "2.0.2-beta" % Test,
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test
    ),
    aars <<= android.Tasks.aarsTaskDef,
    resources in Test := {
      val dir = target.value / "aars"
      dir.mkdirs
      aars.value foreach { lib: LibraryDependency =>
        if ((lib.getFolder / "res").isDirectory)
          IO.copyDirectory(lib.getFolder, dir / lib.getFolder.getName)
      }
      (resources in Test).value
    },
    fork in Test := true,
    parallelExecution in Test := false,
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v"),
    javaOptions in Test ++= Seq("-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled")
	)

lazy val custom       = flavorOf(app, "flavor-custom", Flavor.Custom.settings: _*)
lazy val internal     = flavorOf(app, "flavor-internal", Flavor.Internal.settings: _*)
lazy val experimental = flavorOf(app, "flavor-experimental", Flavor.Experimental.settings: _*)
lazy val candidate    = flavorOf(app, "flavor-candidate", Flavor.Candidate.settings: _*)
lazy val prod         = flavorOf(app, "flavor-prod", Flavor.Prod.settings: _*)
lazy val avs          = flavorOf(app, "flavor-avs", Flavor.Avs.settings: _*)
lazy val qaavs        = flavorOf(app, "flavor-qaavs", Flavor.QAAvs.settings: _*)

lazy val root = Project("zclient", file(".")).aggregate(wireCore, wireUi, app)

lazy val aars = TaskKey[Seq[LibraryDependency]]("aars", "unpack the set of referenced aars")


addCommandAlias("releaseInternal", s""";set zmsVersion in Global := "$zmsDevVersion";flavor-internal/android:packageRelease""")
addCommandAlias("releaseExp", s""";set zmsVersion in Global := "$zmsDevVersion";flavor-experimental/android:packageRelease""")
addCommandAlias("releaseProd", s""";set zmsVersion in Global := "$zmsReleaseVersion";flavor-prod/android:packageRelease""")
addCommandAlias("releaseRC", s""";set zmsVersion in Global := "$zmsReleaseVersion";flavor-candidate/android:packageRelease""")
addCommandAlias("releaseAVS", s""";set zmsVersion in Global := "$zmsReleaseVersion";flavor-avs/android:packageRelease""")
addCommandAlias("releaseQAAVS", s""";set zmsVersion in Global := "$zmsReleaseVersion";flavor-qaavs/android:packageRelease""")
