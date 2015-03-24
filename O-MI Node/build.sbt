import com.github.retronym.SbtOneJar

val scalaBuildVersion = "2.11.4"

scalaVersion := scalaBuildVersion

// build options
scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

// build api options
scalacOptions in (Compile,doc) ++= Seq("-groups", "-implicits", "-diagrams", "-encoding", "utf8")

autoAPIMappings := true 

// STM
//libraryDependencies += ("org.scala-stm" %% "scala-stm" % "0.7")

// SPRAY
libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
	"io.spray"			  %%  "spray-client"  % sprayV,
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "2.3.11" % "test",
	"org.json4s" 		  %%  "json4s-native" % "3.2.11"
  )
}


libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

//slick
libraryDependencies ++= Seq(
  "com.typesafe.slick"  %%  "slick" % "3.0.0-RC1",
  "com.typesafe.slick" %% "slick-codegen" % "3.0.0-RC1",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "org.xerial" % "sqlite-jdbc" % "3.7.2",
  "com.zaxxer" % "HikariCP-java6" % "2.3.3" // XXX: manually updated dependency, slick had 2.0.1
)

cleanFiles <+= baseDirectory { base => base / "sensorDB.sqlite3"  } 

oneJarSettings

Revolver.settings

// Eclipse
EclipseKeys.withSource := true

