/**
  * These settings are common between each of the subprojects
  */
lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion := "2.11.8",
  organization := "com.azavea",
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature"),

  /**
    * We need this merge strategy in order to merge all of our
    * dependencies together - otherwise we get conflicts between
    * dependencies that have the same transitive dependencies trying to
    * overwrite each other.
    */
  assemblyMergeStrategy in assembly := {
    case "log4j.properties" => MergeStrategy.first
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
    case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
    case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  },

  /** This resolver allows us to pull snapshot version from GeoTrellis's BinTray repository */
  resolvers += Resolver.bintrayRepo("azavea", "geotrellis")
)

/**
  * This defines an aggregate project, which just means that if we run
  * commands in this project, it will run them for each of the
  * subprojects.
  */
lazy val root = Project("root", file("."))
  .aggregate(ingest, server)

/**
  * This is the ingest subproject.  See the "ingest/build.sbt" for
  * ingest-specific build settings
  */
lazy val ingest = Project("ingest", file("ingest"))
  .settings(commonSettings: _*)

/**
  * This is the server subproject.  See the "server/build.sbt" for
  * ingest-specific build settings
  */
lazy val server = Project("server", file("server"))
  .settings(commonSettings: _*)
