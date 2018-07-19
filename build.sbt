import com.github.retronym.SbtOneJar
import Dependencies._
import NativePackagerHelper._
import Path.relativeTo
import LinuxPlugin._
import WindowsPlugin._//import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.{Systemd,SystemV,Upstart}
import DebianConstants._
lazy val separator = taskKey[Unit]("Prints seperating string")
separator := println("########################################################\n\n\n\n")

addCommandAlias("release", ";doc ;universal:packageBin ;universal:packageZipTarball ;debian:packageBin ;rpm:packageBin")
addCommandAlias("systemTest", "omiNode/testOnly http.SystemTest")

//mapGenericFilesToLinux
//mapGenericFilesToWindows

// Debugging taskkey 
//val showMappings = taskKey[Unit]("showMappings") 

//showMappings := {
//  (linuxPackageMappings in Rpm).value.foreach{mapping =>
//  println("____________________")
//  mapping.mappings.filter(_._2 contains "conf")
//  .foreach {
//    case (file, path) => println(file + " -> " + path)
//  }
//}
//}

def commonSettings(moduleName: String) = Seq(
  name := s"O-MI-$moduleName",
  version := "1.0.2", // WARN: Release ver must be "x.y.z" (no dashes, '-')
  scalaVersion := "2.12.6",
  scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  javacOptions += "-Xlint:unchecked",
  autoAPIMappings := true,
  exportJars := true,
  EclipseKeys.withSource := true,
  coverageExcludedPackages := "parsing.xmlGen.*;database\\.journal\\.P[A-Z].*;",

  //ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := "parsing.xmlGen.*;"
  testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
  logBuffered := false
)

lazy val JavaDoc = config("genjavadoc") extend Compile

lazy val javadocSettings = inConfig(JavaDoc)(Defaults.configSettings) ++ Seq(
  addCompilerPlugin("com.typesafe.genjavadoc" %% "genjavadoc-plugin" %
    "0.11" cross CrossVersion.full),
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

lazy val omiNode = (project in file("O-MI-Node")).
  //conf(JavaDoc).
  settings(
    (commonSettings("Backend") ++ 
     javadocSettings ++ Seq(
      publish in Docker := {},
      parallelExecution in Test := false,
      //packageDoc in Compile += (baseDirectory).map( _ / html
      cleanFiles += {baseDirectory.value / "logs"},
      //cleanFiles <++= baseDirectory {_ * "*.db" get},
      target in (Compile, doc) := baseDirectory.value / "html" / "api",
      target in (JavaDoc, doc) := baseDirectory.value / "html" / "api" / "java",
      PB.targets in Compile := Seq(
        scalapb.gen() -> (sourceManaged in Compile).value
        ),
      //Revolver.settings,
      libraryDependencies ++= commonDependencies ++ testDependencies)): _*)

lazy val agents = (project in file("Agents")).
  settings(commonSettings("Agents"): _*).
  settings(Seq(
    publish in Docker := {},
    crossTarget := (unmanagedBase in omiNode).value
    )).
    dependsOn(omiNode)

lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging, SystemdPlugin).
  enablePlugins(JavaAgent).//Kamon
  enablePlugins(DockerPlugin).
  //enablePlugins(CodacyCoveragePlugin).
  enablePlugins(RpmPlugin).
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
      maintainer := "Tuomas Kinnunen <tuomas.kinnunen@aalto.fi>",
      packageDescription := "Internet of Things data server",
      packageSummary := """Internet of Things data server implementing Open Messaging Interface and Open Data Format""",

    ///////////////////
    //Docker Settings//
    ///////////////////
      packageName in Docker := "o-mi",
      dockerExposedPorts := Seq(8080, 8180),
      dockerExposedVolumes := Seq("/opt/docker/logs"),
      dockerRepository := Some("aaltoasia"),
      //dockerUsername := Some("aaltoasia"),

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
      bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""",
      bashScriptExtraDefines += """cd  ${app_home}/..""",
      batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dconfig.file=%O_MI_NODE_HOME%\\conf\\application.conf""", 
      batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dlogback.configurationFile=%O_MI_NODE_HOME%\\conf\\logback.xml""", 
      batScriptExtraDefines += """cd "%~dp0\.."""",

    ////////////////////////////
    //Native packager settings//
    ////////////////////////////
    //Mappings tells the plugin which files to include in package and in what directory
      mappings in Universal ++= { directory((baseDirectory in omiNode).value / "html")},
      mappings in Universal ++= {directory(baseDirectory.value / "conf")},
      mappings in Universal ++= { 
        val src = (sourceDirectory in omiNode).value
        val conf = src / "main" / "resources" 
        Seq(
          conf / "reference.conf" -> "conf/application.conf",
          conf / "logback.xml" -> "conf/logback.xml")},
      mappings in Universal ++= {
        println((doc in Compile in omiNode).value)
        val base = (baseDirectory in omiNode).value
        directory(base / "html" / "api").map(n => (n._1, "html/" + n._2))},
      mappings in Universal ++= {
        val base = baseDirectory.value
        Seq(
          base / "tools" / "callbackTestServer.py" -> "callbackTestServer.py",
          base / "README-release.md" -> "README.md",
          base / "LICENSE.txt" -> "LICENSE.txt")},
      mappings in Universal ++= {
        val base = baseDirectory.value
        directory(base / "doc").map(n => (n._1, n._2))},

      rpmVendor := "AaltoASIA",
      rpmUrl := Some("https://github.com/AaltoAsia/O-MI"),
      // Must be in format x.y.z (no dashes)
      // version in Rpm   := 
      rpmLicense := Some("BSD-3-Clause"),
      rpmRelease := "1",
      mappings in Universal := (mappings in Universal).value.distinct,
      //linuxPackageMappings := linuxPackageMappings.value.map{mapping => val filtered = mapping.mappings.toList.distinct;mapping.copy(mappings=filtered)},
      //linuxPackageMappings in Rpm := (linuxPackageMappings in Rpm).value.map{mapping => val filtered = mapping.mappings.toList.distinct;mapping.copy(mappings=filtered)},
      linuxPackageMappings in Rpm := configWithNoReplace((linuxPackageMappings in Rpm).value),
      debianPackageDependencies in Debian ++= Seq("java8-runtime", "bash (>= 2.05a-11)"),

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
  
