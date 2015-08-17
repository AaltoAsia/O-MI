import com.github.retronym.SbtOneJar
import Dependencies._
import NativePackagerHelper._
import Path.relativeTo

def commonSettings(moduleName: String) = Seq(
  name := s"O-MI-$moduleName",
  version := "0.1.7-SNAPSHOT",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-feature", "-encoding", "utf8", "-Xlint"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  autoAPIMappings := true,
  exportJars := true, //might not be needed if we don't use oneJar
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
//TODO omiNode should depend on agent jar
lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging).
  settings(
    (commonSettings("root") ++ Seq(
   //maintainer := "John Smith <john.smith@example.com>",
   // packageDescription := "TempName",
   // packageSummary := "TempName",
   // entrypoint
      mainClass in Compile := Some("http.Boot"),
	  //TODO configs and deploy directories need to be on the same path from where you run the start script as well as the SmartHouse.xml and otaniemi3d-data.xml
	  //to get stuff working atm you have to move files from stage directory to bin directory (after running 'sbt stage')
      //mappings in Universal <++= (sourceDirectory in omiNode) map (src => directory(src / "main" / "resources" / "html")),
	  mappings in Universal <++= (baseDirectory in omiNode) map (src => directory(src / "configs")),
	  mappings in Universal <++= (baseDirectory in omiNode) map (src => directory(src / "deploy")),
	  mappings in Universal <+=  (packageBin in Compile, sourceDirectory in omiNode) map { (_,src) =>
	    val conf = src / "main" / "resources" / "application.conf"
		conf -> "conf/application.conf"
      },
	  mappings in Universal <++= (baseDirectory in omiNode) map { base => 
	    val smarthouse = base / "SmartHouse.xml"
		val otaniemi3d = base / "otaniemi3d-data.xml"
		Seq(
		  smarthouse -> "SmartHouse.xml",
		  otaniemi3d -> "otaniemi3d-data.xml"
		)
	  }
	)):_*).
  aggregate(omiNode,agents).
  dependsOn(omiNode,agents)
  