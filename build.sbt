import com.github.retronym.SbtOneJar
import Dependencies._
import NativePackagerHelper._
import Path.relativeTo
import com.typesafe.sbt.packager.archetypes.ServerLoader.{Systemd,SystemV,Upstart}

lazy val separator = taskKey[Unit]("Prints seperating string")
separator := println("########################################################\n\n\n\n")

addCommandAlias("release", ";doc;universal:packageBin;universal:packageZipTarball")
addCommandAlias("systemTest", "omiNode/testOnly http.SystemTest")


def commonSettings(moduleName: String) = Seq(
  name := s"O-MI-$moduleName",
  version := "0.9.0-RC1",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  javacOptions += "-Xlint:unchecked",
  autoAPIMappings := true,
  exportJars := true,
  EclipseKeys.withSource := true,
  // coverage 1.3.x:
  coverageExcludedPackages := "parsing.xmlGen.*;",
  // coverage 1.0.x:
  //ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := "parsing.xmlGen.*;"
  testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
  logBuffered := false
)

lazy val JavaDoc = config("genjavadoc") extend Compile

//Something is broken
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
  //configs(JavaDoc).
  settings(
    (commonSettings("Backend") ++ 
     javadocSettings ++ Seq(
      parallelExecution in Test := false,
      //packageDoc in Compile += (baseDirectory).map( _ / html
      cleanFiles += {baseDirectory.value / "logs"},
      //cleanFiles <++= baseDirectory {_ * "*.db" get},
      target in (Compile, doc) := baseDirectory.value / "html" / "api",
      target in (JavaDoc, doc) := baseDirectory.value / "html" / "api" / "java",
      //Revolver.settings,
      libraryDependencies ++= commonDependencies ++ testDependencies)): _*) //  ++ servletDependencies

lazy val agents = (project in file("Agents")).
  settings(commonSettings("Agents"): _*).
  settings(Seq(
    libraryDependencies ++= commonDependencies,
    crossTarget := (unmanagedBase in omiNode).value
    )).
    dependsOn(omiNode)

lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging).
  enablePlugins(DockerPlugin).
  //enablePlugins(SystemdPlugin).
  //enablePlugins(CodacyCoveragePlugin).
  settings(commonSettings("Node")).
  settings(
    Seq(
    /////////////////////////////////
    //Starting point of the program//
    /////////////////////////////////
      mainClass in Compile := Some("http.Boot"),

    ///////////////////////
    //Package information//
    ///////////////////////
      maintainer := "Andrea Buda <andrea.buda@aalto.fi>",
      packageDescription := "Internet of Things data server",
      packageSummary := """Internet of Things data server implementing Open Messaging Interface and Open Data Format""",

    ///////////////////
    //Docker Settings//
    ///////////////////
      packageName in Docker := "o-mi-reference",
      dockerExposedPorts := Seq(8080, 8180),

    ////////////////////////////////////////////////
    //Locations to be cleared when using sbt clean//
    ////////////////////////////////////////////////
      cleanFiles ++= {
        val base = (baseDirectory in omiNode).value
        Seq(
          base / "html" / "api",
          base / "lib",
          base / "logs",
          file("logs"))},
    
    ////////////////////////////////////////////////////////////////////////
    //Update version file so that the web browser displays current version//
    ////////////////////////////////////////////////////////////////////////
      resourceGenerators in Compile += Def.task {
        val file =  (baseDirectory in Compile in omiNode).value / "html" / "VERSION"
        IO.write(file, s"${version.value}")
        Seq(file)},

    ///////////////////////////////////////////////////////////////////////
    //Configure program to read application.conf from the right direction//
    ///////////////////////////////////////////////////////////////////////
      bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../configs/application.conf"""",
      bashScriptExtraDefines += """cd  ${app_home}/..""",
      batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dconfig.file=%O_MI_NODE_HOME%\\configs\\application.conf""", 
      batScriptExtraDefines += """cd "%~dp0\.."""",

    ////////////////////////////
    //Native packager settings//
    ////////////////////////////
      serverLoading in Debian := Systemd,
    //Mappings tells the plugin which files to include in package and in what directory
      mappings in Universal ++= { directory((baseDirectory in omiNode).value / "html")},
      mappings in Universal ++= {directory(baseDirectory.value / "configs")},
      mappings in Universal += { 
        println((packageBin in Compile).value)
        val src = (sourceDirectory in omiNode).value
        val conf = src / "main" / "resources" / "application.conf"
        conf -> "configs/application.conf"},
      mappings in Universal ++= {
        println((doc in Compile in omiNode).value)
        val base = (baseDirectory in omiNode).value
        directory(base / "html" / "api").map(n => (n._1, "html/" + n._2))},
      mappings in Universal ++= {
        val base = baseDirectory.value
        Seq(
          base / "tools" / "callbackTestServer.py" -> "callbackTestServer.py",
          base / "README-release.md" -> "README.md",
          base / "AgentDeveloperGuide.md" -> "AgentDeveloperGuide.md",
          base / "GettingStartedGuide.md" -> "GettingStartedGuide.md",
          base / "LICENSE.txt" -> "LICENSE.txt")},

    /////////////////////////////////////////////////////////////
    //Prevent aggregation of following commands to sub projects//
    /////////////////////////////////////////////////////////////
      aggregate in reStart := false,
      aggregate in reStop := false
      ): _*
  ).
  aggregate(omiNode, agents).
  dependsOn(agents)

// Choose Tomcat or Jetty default settings and build a .war file with `sbt package`
tomcat()
// jetty()

  
