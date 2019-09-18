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
  //scalaVersion := "2.13.0-M2",
  scalaOrganization := "org.typelevel",
  scalaVersion      := "2.12.4-bin-typelevel-4",
  //scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint", s"-P:genjavadoc:out=${target.value}/java"),
  scalacOptions ++= Seq(
    "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                // Specify character encoding used by source files.
    "-explaintypes",                     // Explain type errors in more detail.
    "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
    "-language:higherKinds",             // Allow higher-kinded types
    "-language:implicitConversions",     // Allow definition of implicit functions called views
    "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
    //"-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
    "-Xfuture",                          // Turn on future language features.
    "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
    "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
    "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",            // Option.apply used implicit view.
    "-Xlint:package-object-classes",     // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match",              // Pattern match may not be typesafe.
    "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ypartial-unification",             // Enable partial unification in type constructor inference
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard",              // Warn when non-Unit expression results are unused.
    // TYPELEVEL SCALA 4
    "-Yinduction-heuristics",       // speeds up the compilation of inductive implicit resolution
    "-Ykind-polymorphism",          // type and method definitions with type parameters of arbitrary kinds
    "-Yliteral-types",              // literals can appear in type position
    //"-Xstrict-patmat-analysis",     // more accurate reporting of failures of match exhaustivity
    //"-Xlint:strict-unsealed-patmat" // warn on inexhaustive matches against unsealed traits
  ),
  scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-implicits", "-diagrams", "-diagrams-debug"),
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

//lazy val Javadoc = config("genjavadoc") extend Compile
//
//lazy val javadocSettings = inConfig(Javadoc)(Defaults.configSettings) ++ Seq(
//  addCompilerPlugin("com.typesafe.genjavadoc" %% "genjavadoc-plugin" % "0.11" cross CrossVersion.full),
//  packageDoc in Compile := (packageDoc in Javadoc).value,
//  sources in Javadoc :=
//    (target.value / "java" ** "*.java").get ++
//    (sources in Compile).value.filter(_.getName.endsWith(".java")),
//    javacOptions in Javadoc := Seq(),
//    artifactName in packageDoc in Javadoc := ((sv, mod, art) =>
//        "" + mod.name + "_" + sv.binary + "-" + mod.revision + "-javadoc.jar")
//      )

lazy val omiNode = (project in file("O-MI-Node")).
  //enablePlugins(GenJavadocPlugin).
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
      addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      libraryDependencies ++= commonDependencies ++ testDependencies)): _*)

lazy val agents = (project in file("Agents")).
  //enablePlugins(GenJavadocPlugin).
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
  enablePlugins(LauncherJarPlugin).
  //configs(Javadoc).
  //settings(javadocSettings: _*).
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
  
