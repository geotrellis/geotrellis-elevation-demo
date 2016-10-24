name := "ingest"

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-spark-etl" % "1.0.0-5d71f65",
  "org.apache.spark"      %% "spark-core" % "2.0.1" % "provided"
)

// We need this merge strategy in order to
// merge all of our dependencies together -
// otherwise we get conflicts between dependencies
// that have the same transitive dependencies trying
// to overwrite each other.
assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}
