
val scalaBuildVersion = "2.11.2"

scalaVersion := scalaBuildVersion

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")


// SPRAY
libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq(
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
  )
}


// Eclipse
EclipseKeys.withSource := true

