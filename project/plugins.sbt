resolvers += Classpaths.sbtPluginReleases
resolvers ++= Seq(
  Resolver.jcenterRepo,
  Resolver.bintrayRepo("sksamuel", "sbt-plugins")
)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("org.scala-android" % "sbt-android" % "1.7.10")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.1")

scalacOptions ++= Seq("-feature", "-encoding", "UTF-8", "-deprecation")
