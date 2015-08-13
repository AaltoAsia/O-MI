import com.github.retronym.SbtOneJar
import Dependencies._

def commonSettings(moduleName: String) = Seq(
  name := s"O-MI-$moduleName",
  version := "0.1.7-SNAPSHOT",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-feature", "-encoding", "utf8", "-Xlint"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  autoAPIMappings := true,
  exportJars := true,
  EclipseKeys.withSource := true,
  ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := "parsing.xmlGen.*;"
  )

lazy val omiNode = (project in file("O-MI Node")).
  settings(
    (commonSettings("Node") ++ Seq(
	parallelExecution in Test := false,
	Revolver.settings,
	cleanFiles <++= baseDirectory {_ * "*.db" get}
	)):_*
  ).
  settings(
    libraryDependencies ++= commonDependencies ++ servletDependencies ++ testDependencies
  )
  
lazy val agents = (project in file("Agents")).
  settings(commonSettings("Agents"): _*
  ).
  settings(
    libraryDependencies ++= commonDependencies
  ).
  dependsOn(omiNode)
//  
//  lazy val root = Project(
//    id = "root",
//    base = file("."),
//    // configure your native packaging settings here
//	enablePlugins(JavaServerAppPackaging),
//    settings = Seq(
//	  maintainer := "John Smith <john.smith@example.com>",
//      packageDescription := "Fullstack Application",
//      packageSummary := "Fullstack Application",
//      // entrypoint
//      mainClass in Compile := Some("http.boot")
//      ),
//    // always run all commands on each sub project
//    aggregate(agents, omiNode)
//    ) dependsOn(agents, omiNode)
lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging).
  settings(
    (commonSettings("root") ++ Seq(
	maintainer := "John Smith <john.smith@example.com>",
    packageDescription := "TempName",
    packageSummary := "TempName",
   // entrypoint
    mainClass in Compile := Some("omi.ProductionServer")
	)):_*).
  aggregate(omiNode,agents).
  dependsOn(omiNode,agents)
  