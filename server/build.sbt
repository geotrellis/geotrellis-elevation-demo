name := "server"

val akkaVersion = "2.4.10"
val geotrellisVersion = "1.0.0-SNAPSHOT"
val hadoopVersion = "2.7.1"
val sparkVersion = "2.0.1"
val sprayVersion = "1.3.3"

libraryDependencies ++= (
  Seq(
    "com.typesafe.akka" %% "akka-kernel"  % akkaVersion,
    "com.typesafe.akka" %% "akka-remote"  % akkaVersion,
    "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.azavea.geotrellis" %% "geotrellis-spark" % geotrellisVersion,
    "io.spray"              %% "spray-can"        % sprayVersion,
    "io.spray"              %% "spray-routing"    % sprayVersion,
    "org.apache.hadoop"      % "hadoop-client"    % hadoopVersion,
    "org.apache.spark"      %% "spark-core"       % sparkVersion % "provided"
  )
    .map(_.exclude("com.google.guava", "guava"))
    ++ Seq("com.google.guava" % "guava" % "16.0.1"))

fork in Test := false
parallelExecution in Test := false

Revolver.settings
