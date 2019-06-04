import Dependencies._
import NativePackagerHelper._
import Path.relativeTo
import LinuxPlugin._
import com.typesafe.sbt.packager.linux.LinuxSymlink
import WindowsPlugin._//import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.{Systemd,SystemV,Upstart}
import DebianConstants._
lazy val separator = taskKey[Unit]("Prints seperating string")
separator := println("########################################################\n\n\n\n")

addCommandAlias("release", ";universal:packageBin ;universal:packageZipTarball ;debian:packageBin ;rpm:packageBin ;unidoc ")
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
  version := "2.0.1", // WARN: Release ver must be "x.y.z" (no dashes, '-')
  scalaVersion := "2.12.6",
  scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint", s"-P:genjavadoc:out=${target.value}/java"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  //javacOptions += "-Xlint:unchecked",
  autoAPIMappings := true,
  exportJars := true,
  EclipseKeys.withSource := true,
  Test / fork := true,
  coverageExcludedPackages := "parsing.xmlGen.*;database\\.journal\\.P[A-Z].*;types.OdfTypes.*;",

  //ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := "parsing.xmlGen.*;"
  testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
  logBuffered := false
)

lazy val Javadoc = config("genjavadoc") extend Compile

lazy val javadocSettings = inConfig(Javadoc)(Defaults.configSettings) ++ Seq(
  addCompilerPlugin("com.typesafe.genjavadoc" %% "genjavadoc-plugin" % "0.11" cross CrossVersion.full),
  packageDoc in Compile := (packageDoc in Javadoc).value,
  sources in Javadoc :=
    (target.value / "java" ** "*.java").get ++
    (sources in Compile).value.filter(_.getName.endsWith(".java")),
    javacOptions in Javadoc := Seq(),
    artifactName in packageDoc in Javadoc := ((sv, mod, art) =>
        "" + mod.name + "_" + sv.binary + "-" + mod.revision + "-javadoc.jar")
      )

lazy val omiNode = (project in file("O-MI-Node")).
  enablePlugins(GenJavadocPlugin).
  settings(
    (commonSettings("Backend") ++ 
     Seq(
      publish in Docker := {},
      parallelExecution in Test := false,
      //packageDoc in Compile += (baseDirectory).map( _ / html
      cleanFiles += {baseDirectory.value / "logs"},
      //cleanFiles <++= baseDirectory {_ * "*.db" get},
     // target in (JavaDoc, doc) := baseDirectory.value / "html" / "api" / "java",
      PB.targets in Compile := Seq(
        scalapb.gen() -> (sourceManaged in Compile).value
        ),
      //Revolver.settings,
      libraryDependencies ++= commonDependencies ++ testDependencies)): _*)

lazy val agents = (project in file("Agents")).
  enablePlugins(GenJavadocPlugin).
  settings(commonSettings("Agents"): _*).
  settings(Seq(
    publish in Docker := {},
    crossTarget := (unmanagedBase in omiNode).value
    )).
    dependsOn(omiNode)

lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging, SystemdPlugin).
  //enablePlugins(JavaAgent).//Kamon
  enablePlugins(DockerPlugin).
  //enablePlugins(CodacyCoveragePlugin).
  enablePlugins(RpmPlugin).
  enablePlugins(ScalaUnidocPlugin).
  enablePlugins(JavaUnidocPlugin).
  configs(Javadoc).
  settings(javadocSettings: _*).
  settings(commonSettings("Node")).
  settings(
    Seq(
      target in unidoc in JavaUnidoc := (baseDirectory in omiNode).value / "html" / "api" / "java",
      target in unidoc in ScalaUnidoc := (baseDirectory in omiNode).value / "html" / "api",
         //unidoc / target := baseDirectory.value / "html" / "api",
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
      dockerUpdateLatest := true,

    ////////////////////////////////////////////////
    //Locations to be cleared when using sbt clean//
    ////////////////////////////////////////////////
      cleanFiles ++= {
        val base = (baseDirectory in omiNode).value
        Seq(
          base / "html" / "api",
          base / "lib",
          base / "logs",
          base / "database",
          base / "database" / "valuehistorydb",
          base / "database" /"journaldb",
          file("logs"),
          file("database"),
          file("valuehistorydb"),
          file("journaldb"))},
    
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
      bashScriptExtraDefines += """cd  "${app_home}/.."""",
      batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dconfig.file=%O_MI_NODE_HOME%\\conf\\application.conf""", 
      batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dlogback.configurationFile=%O_MI_NODE_HOME%\\conf\\logback.xml""", 
      batScriptExtraDefines += """cd "%~dp0\.."""",

    ////////////////////////////
    //Native packager settings//
    ////////////////////////////
      //AspectJWeaver for Kamon to run with native-packager
      //javaAgents += "org.aspectj" % "aspectjweaver" % "1.8.13",
      //javaOptions in Universal += "-Dorg.aspectj.tracing.factory=default",
    //Mappings tells the plugin which files to include in package and in what directory
      mappings in Universal ++= { directory((baseDirectory in omiNode).value / "html")},
      mappings in Universal ++= {directory(baseDirectory.value / "conf")},
      mappings in Universal ++= { 
        val src = (sourceDirectory in omiNode).value
        val conf = src / "main" / "resources" 
        Seq(
          conf / "reference.conf" -> "conf/application.conf",
          conf / "logback.xml" -> "conf/logback.xml")},
      /*
      mappings in Universal ++= {
        println((doc in Compile in omiNode).value)
        val base = (baseDirectory in omiNode).value
        directory(base / "html" / "api").map(n => (n._1, "html/" + n._2))},
      */
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
      /**
       * TODO: Start using after sorting out possible issues and checking
       * that structure is in wanted format. Should solve issues with wrong
       * permissions preventing creating files.
       */
      //AspectJWeaver for Kamon to run with native-packager
      //javaAgents += "org.aspectj" % "aspectjweaver" % "1.8.13",
      
      // WARNING ON COMPILE, CONFLICTS WITH application.ini
      //javaOptions in Universal += "-Dorg.aspectj.tracing.factory=default",

    //Create empty database directory for Tar. Zip removes empty directories?
    //TODO: Check Warp10, uses database directory.
    mappings in (Universal,packageZipTarball) ++= {
      val base = baseDirectory.value
      Seq( base -> "database/")
    },
    mappings in (Universal,packageBin) ++= {
      val base = baseDirectory.value
      Seq( base  -> "database/")
    },
    // Create directories to /var/lib/o-mi-node with correct permissions and add
    // symlinks for them.
      linuxPackageMappings ++= Seq(
          packageTemplateMapping(
            s"/var/lib/${normalizedName.value}/"
          )() withUser( daemonUser.value ) withGroup( daemonGroup.value ),
          packageTemplateMapping(
            s"/var/lib/${normalizedName.value}/database"
          )() withUser( daemonUser.value ) withGroup( daemonGroup.value ),
          packageTemplateMapping(
            s"/var/lib/${normalizedName.value}/database/valuehistorydb"
          )() withUser( daemonUser.value ) withGroup( daemonGroup.value ),
          packageTemplateMapping(
            s"/var/lib/${normalizedName.value}/database/journaldb"
          )() withUser( daemonUser.value ) withGroup( daemonGroup.value )
        ),
      linuxPackageSymlinks ++=Seq(
        LinuxSymlink( s"/usr/share/${normalizedName.value}/database/valuehistorydb", s"/var/lib/${normalizedName.value}/database/valuehistorydb"),
        LinuxSymlink( s"/usr/share/${normalizedName.value}/database/journaldb", s"/var/lib/${normalizedName.value}/database/journaldb"),
      ),
      linuxPackageMappings in Rpm := configWithNoReplace((linuxPackageMappings in Rpm).value),
      debianPackageDependencies in Debian ++= Seq("java8-runtime", "bash (>= 2.05a-11)"),
      //debianNativeBuildOptions in Debian := Nil, // dpkg-deb's default compression (currently xz)
      debianNativeBuildOptions in Debian := Seq("-Zgzip", "-z3"), // gzip compression at level 3

    /////////////////////////////////////////////////////////////
    //Prevent aggregation of following commands to sub projects//
    /////////////////////////////////////////////////////////////
      aggregate in reStart := false,
      javaOptions in reStart ++= Seq("-XX:+UseG1GC", "-Xms128m", "-Xmx4g", "-XX:+CMSClassUnloadingEnabled"),
      aggregate in reStop := false
      ): _*
  ).
  aggregate(omiNode, agents).
  dependsOn(agents)

// Choose Tomcat or Jetty default settings and build a .war file with `sbt package`
enablePlugins(TomcatPlugin)
//enablePlugins(JettyPlugin)
  
