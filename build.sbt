import com.github.retronym.SbtOneJar
import Dependencies._

val commonSettings = Seq(
  version := "0.1.7-SNAPSHOT",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-feature", "-encoding", "utf8", "-Xlint"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  autoAPIMappings := true,
  //Eclipse
  EclipseKeys.withSource := true
  )

lazy val omiNode = (project in file("O-MI Node")).
  settings(
    (commonSettings ++ Seq(
	parallelExecution in Test := false)
	):_*
  ).
  settings(
    libraryDependencies ++= commonDependencies ++ servletDependencies ++ testDependencies
  )
  
lazy val agents = (project in file("Agents")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= commonDependencies
  ).
  dependsOn(omiNode)
  
//  enablePlugins(JavaAppPackaging)
  
  Revolver.settings