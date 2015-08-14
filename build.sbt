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

lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging).
  settings(
    (commonSettings("root") ++ Seq(
   //maintainer := "John Smith <john.smith@example.com>",
   // packageDescription := "TempName",
   // packageSummary := "TempName",
   // entrypoint
      mainClass in Compile := Some("http.Boot"),
	mappings in Universal := {
	  val universalMappings = (mappings in Universal).value
	  val resourceDir = (resourceDirectory in Compile).value
	  val directories = Seq("configs", "deploy", "html")
	  val nodeBase = (baseDirectory in omiNode).value
	  var fileList = Seq(
	    resourceDir / "application.conf" -> "application.conf",
        nodeBase / "start.sh" -> "start.sh",
        nodeBase / "start.bat" -> "start.bat",
        baseDirectory / "callbackTestServer.py" -> "callbackTestServer.py",
        nodeBase / "SmartHouse.xml" -> "SmartHouse.xml",
        nodeBase / "otaniemi3d-data.xml" -> "otaniemi3d-data.xml",
        nodeBase / "README-release.md" -> "README.md"
	  )
	  directories foreach { dir =>
        val paths = Path.allSubpaths(nodeBase / dir)
        val pathsWithoutVimBac = paths filter {_._1.toString.last != '~'}
        fileList ++=
        pathsWithoutVimBac map {case (path, relative) => path -> (dir + "/" + relative)}
      }
	  universalMappings ++ fileList
	  }
	)):_*).
  aggregate(omiNode,agents).
  dependsOn(omiNode,agents)
  