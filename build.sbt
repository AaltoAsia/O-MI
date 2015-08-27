import com.github.retronym.SbtOneJar
import Dependencies._
import NativePackagerHelper._
import Path.relativeTo

addCommandAlias("release", "universal:package-bin")
addCommandAlias("systemTest", "omiNode/testOnly http.SystemTest")

def commonSettings(moduleName: String) = Seq(
  name := s"O-MI-$moduleName",
  version := "0.1.7-SNAPSHOT",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  autoAPIMappings := true,
  exportJars := true,
  EclipseKeys.withSource := true,
  ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := "parsing.xmlGen.*;")

lazy val omiNode = (project in file("O-MI Node")).
  settings(
    (commonSettings("Backend") ++ Seq(
      parallelExecution in Test := false,
      Revolver.settings,
      cleanFiles <++= baseDirectory { _ * "*.db" get },
      libraryDependencies ++= commonDependencies ++ servletDependencies ++ testDependencies)): _*)

lazy val agents = (project in file("Agents")).
  settings(commonSettings("Agents"): _*).
  settings(
    libraryDependencies ++= commonDependencies).
    dependsOn(omiNode)

lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging).
  settings(
    (commonSettings("Node") ++ Seq(
      //maintainer := "John Smith <john.smith@example.com>",
      //packageDescription := "TempName",
      //packageSummary := "TempName",
      resourceGenerators in Compile <+= (baseDirectory in Compile, version) map { (dir, currentVersion) =>
        val file = dir / "html" / "VERSION"
        IO.write(file, s"${currentVersion}")
        Seq(file)
      },
      bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      mainClass in Compile := Some("http.Boot"),
      mappings in Universal <++= baseDirectory map (src => directory(src / "html")),
      mappings in Universal <++= baseDirectory map (src => directory(src / "configs")),
      mappings in Universal <+= (packageBin in Compile, sourceDirectory in omiNode) map { (_, src) =>
        val conf = src / "main" / "resources" / "application.conf"
        conf -> "conf/application.conf"
      },
      mappings in Universal <++= (packageDoc in Compile in omiNode, target in omiNode) map { (_, target) =>
        directory(target / "scala-2.11" / "api")
      },
      mappings in Universal <++= baseDirectory map { base =>
        Seq(
          base / "SmartHouse.xml" -> "SmartHouse.xml",
          base / "otaniemi3d-data.xml" -> "otaniemi3d-data.xml",
          base / "callbackTestServer.py" -> "callbackTestServer.py",
          base / "README-release.md" -> "README.md")
      })): _*).
    aggregate(omiNode, agents).
    dependsOn(omiNode, agents)

// Choose Tomcat or Jetty default settings and build a .war file with `sbt package`
tomcat()
// jetty()

  
