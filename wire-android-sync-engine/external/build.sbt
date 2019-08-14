import sbt.Keys._
import ProguardKeys._

lazy val commonSettings = Seq(
  organization := "com.wire",
  bintrayOrganization := Some("wire-android"),
  bintrayRepository := "third-party",
  licenses += ("GPL-3.0", url("https://opensource.org/licenses/GPL-3.0")),
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false,
  publishMavenStyle := true,
  crossPaths := false,
  autoScalaLibrary := false,
  ivyConfigurations += Hidden
) ++ inConfig(Hidden)(Defaults.compileSettings)

lazy val Hidden = config("hidden").hide


lazy val root = Project("wire-external", file("."))
  .settings(
    publish := { },
    publishLocal := { }
  )
  .aggregate(icu4j, spotifyPlayer)

lazy val icu4j = project
  .settings(proguardSettings:_ *)
  .settings(commonSettings:_ *)
  .settings(
    name := "icu4j-shrunk",
    version := "57.1",
    packageBin in Compile := (proguard in Proguard).value.head,
    javaHome := Some(file(sys.props("java.home"))),
    libraryDependencies += "com.ibm.icu" % "icu4j" % version.value % Hidden,
    inConfig(Proguard)(Seq(
      inputs := (externalDependencyClasspath in Hidden).value.files,
      inputFilter := (_ => Some("!**Currency**,!**Grego.class,!**/Holiday**.class,**.class,**/brkitr/root.res,**/brkitr/char.brk,**/brkitr/word.brk,**/brkitr/line.brk,**/brkitr/thaidict.dict,**/translit/*,**/ucase.icu,**/pnames.icu,**/uprops.icu,**.nrm")),
      binaryDeps := Seq(javaHome.value.get / "lib" / "rt.jar"),
      options ++= Seq("-dontnote", "-dontwarn", "-ignorewarnings", "-dontobfuscate", "-dontoptimize", "-keep",
        """public class com.ibm.icu.text.Transliterator {
          |    public static final com.ibm.icu.text.Transliterator getInstance(java.lang.String);
          |    public final java.lang.String transliterate(java.lang.String);
          |}""".stripMargin
      )
    ))
  )

def spotifyDownloadUrl(version: String) =
  s"https://github.com/spotify/android-sdk/releases/download/$version/SpotifySdkAndroid-$version.zip"

lazy val spotifySdk = taskKey[File]("Extracted spotify sdk dir")

lazy val spotifySettings = commonSettings ++ Seq(
  version := "1.0.0-beta13",
  libraryDependencies += "com.spotify.sdk" % "spotify-sdk" % version.value  % Hidden from spotifyDownloadUrl(version.value),
  spotifySdk := {
    val v = version.value
    val target = crossTarget.value / "spotify-sdk"
    target.mkdirs()
    update.value.select(artifact = artifactFilter(extension = "zip")) .foreach { archive => IO.unzip(archive, target) }
    target / s"SpotifySdkAndroid-$v"
  },
  packageBin in Compile := {
    val v = version.value
    val n = name.value
    spotifySdk.value / s"$n-$v.aar"
  },
  artifact in packageBin in Compile := (artifact in packageBin in Compile).value.copy(extension = "aar", `type` = "aar")
)

lazy val spotifyPlayer = project.settings(spotifySettings: _*).settings(name := "spotify-player")


def cryptoboxDownloadUrl(version: String, artifact: String = "cryptobox-android", ext: String = "aar") =
  s"https://github.com/wireapp/cryptobox-jni/releases/download/v$version/$artifact-$version.$ext"

lazy val cryptoBox = project
  .settings(commonSettings: _*)
  .settings(
    name := "cryptobox-android",
    bintrayRepository := "releases",
    version := "0.8.1",
    libraryDependencies += "com.wire.github" % "cryptobox-android" % version.value % Hidden from cryptoboxDownloadUrl(version.value),
    packageBin in Compile := update.value.select(artifact = artifactFilter(extension = "aar")).head,
    artifact in packageBin in Compile := (artifact in packageBin in Compile).value.copy(extension = "aar", `type` = "aar")
  )

lazy val cryptoBoxOsx = project
  .settings(commonSettings: _*)
  .settings(
    name := "cryptobox-jni-osx",
    bintrayRepository := "releases",
    version := "0.8.1",
    libraryDependencies += "com.wire.github" % "cryptobox-jni-osx" % version.value % Hidden from cryptoboxDownloadUrl(version.value, "cryptobox-jni-darwin-x86_64", "tar.gz"),
    packageBin in Compile := update.value.select(artifact = artifactFilter(extension = "gz")).head,
    artifact in packageBin in Compile := (artifact in packageBin in Compile).value.copy(extension = "tgz", `type` = "tgz")
  )
