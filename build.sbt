import com.github.retronym.SbtOneJar
import Dependencies._
import NativePackagerHelper._
import Path.relativeTo
import com.typesafe.sbt.packager.archetypes.ServerLoader.{SystemV,Upstart}

addCommandAlias("release", ";doc;universal:packageBin;universal:packageZipTarball")
addCommandAlias("systemTest", "omiNode/testOnly http.SystemTest")


def commonSettings(moduleName: String) = Seq(
  name := s"O-MI-$moduleName",
  version := "0.3.0",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  autoAPIMappings := true,
  exportJars := true,
  EclipseKeys.withSource := true,
  ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := "parsing.xmlGen.*;")

lazy val JavaDoc = config("genjavadoc") extend Compile

lazy val javadocSettings = inConfig(JavaDoc)(Defaults.configSettings) ++ Seq(
  addCompilerPlugin("com.typesafe.genjavadoc" %% "genjavadoc-plugin" %
    "0.9" cross CrossVersion.full),
  scalacOptions += s"-P:genjavadoc:out=${target.value}/java",
  packageDoc in Compile := (packageDoc in JavaDoc).value,
  sources in JavaDoc := 
    (target.value / "java" ** "*.java").get ++ (sources in Compile).value.
      filter(_.getName.endsWith(".java")),
  javacOptions in JavaDoc := Seq(),
  artifactName in packageDoc in JavaDoc :=
    ((sv, mod, art) =>
      "" + mod.name + "_" + sv.binary + "-" + mod.revision + "-javadoc.jar")
)

lazy val omiNode = (project in file("O-MI Node")).
  configs(JavaDoc).
  settings(
    (commonSettings("Backend") ++ 
     javadocSettings ++ Seq(
      parallelExecution in Test := false,
      //packageDoc in Compile += (baseDirectory).map( _ / html
      cleanFiles <+= baseDirectory { base => base / "logs"},
      //cleanFiles <++= baseDirectory {_ * "*.db" get},
      target in (Compile, doc) := baseDirectory.value / "html" / "api",
      //Revolver.settings,
      libraryDependencies ++= commonDependencies ++ servletDependencies ++ testDependencies)): _*)

lazy val agents = (project in file("Agents")).
  settings(commonSettings("Agents"): _*).
  settings(Seq(
    libraryDependencies ++= commonDependencies,
    crossTarget <<= (unmanagedBase in omiNode)
    )).
    dependsOn(omiNode)

lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging).
  settings(
    (commonSettings("Node") ++ Seq(
      maintainer := "Andrea Buda <andrea.buda@aalto.fi>",
      packageDescription := "Internet of Things data server",
      packageSummary := """Internet of Things data server implementing Open Messaging Interface and Open Data Format""",
      cleanFiles <++= (baseDirectory in omiNode) {base => Seq(
        base / "html" / "api",
        base / "lib",
        base / "logs",
        file("logs"))},
      serverLoading in Debian := SystemV,
      //(Compile,doc) in omiNode := (baseDirectory).map{n=> 
      //  n / "html" / "api"},
      resourceGenerators in Compile <+= (baseDirectory in Compile in omiNode, version) map { (dir, currentVersion) =>
        val file = dir / "html" / "VERSION"
        IO.write(file, s"${currentVersion}")
        Seq(file)
      },
      bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../configs/application.conf"""",
      bashScriptExtraDefines += """cd  ${app_home}/..""",
      batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dconfig.file=%O_MI_NODE_HOME%\\configs\\application.conf""", 
      batScriptExtraDefines += """cd "%~dp0\.."""",
      mainClass in Compile := Some("http.Boot"),
      mappings in Universal <++= (baseDirectory in omiNode) map (src => directory(src / "html")),
      mappings in Universal <++= (baseDirectory in omiNode) map (src => directory(src / "configs")),
      mappings in Universal <+= (packageBin in Compile, sourceDirectory in omiNode) map { (_, src) =>
        val conf = src / "main" / "resources" / "application.conf"
        conf -> "configs/application.conf"
      },
      mappings in Universal <++= (doc in Compile in omiNode, target in omiNode) map { (_, target) =>
        directory(target / "scala-2.11" / "api").map(n => (n._1, "html/" + n._2))
      },
      mappings in Universal <++= (baseDirectory in omiNode) map { base =>
        Seq(
          base / "configs" / "SmartHouse.xml" -> "SmartHouse.xml")
      },
      mappings in Universal <++= baseDirectory map { base =>
        Seq(
          base / "callbackTestServer.py" -> "callbackTestServer.py",
          base / "README-release.md" -> "README.md",
          base / "AgentDeveloperGuide.md" -> "AgentDeveloperGuide.md",
          base / "GettingStartedGuide.md" -> "GettingStartedGuide.md",
          base / "LICENSE.txt" -> "LICENSE.txt")
      },
      aggregate in reStart := false,
      aggregate in reStop := false
      )): _*).
    aggregate(omiNode, agents).
    dependsOn(agents)

// Choose Tomcat or Jetty default settings and build a .war file with `sbt package`
tomcat()
// jetty()

  
