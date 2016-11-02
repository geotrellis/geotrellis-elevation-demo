name := "ingest"

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-spark-etl" % "1.0.0-SNAPSHOT",
  "org.apache.spark"      %% "spark-core" % "2.0.1" % "provided"
)

fork in Test := false
parallelExecution in Test := false
