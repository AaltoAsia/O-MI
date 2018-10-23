import Dependencies._
import NativePackagerHelper._
import Path.relativeTo
import LinuxPlugin._
import WindowsPlugin._//import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.{Systemd,SystemV,Upstart}
import DebianConstants._
lazy val separator = taskKey[Unit]("Prints seperating string")
separator := println("########################################################\n\n\n\n")

addCommandAlias("release", ";unidoc ;universal:packageBin ;universal:packageZipTarball ;debian:packageBin ;rpm:packageBin")
addCommandAlias("systemTest", "omiNode/testOnly http.SystemTest")

//update Both when updating (windows has two %% for url escaping)
val unixWarp10URL = "https://bintray.com/cityzendata/generic/download_file?file_path=io%2Fwarp10%2Fwarp10%2F1.2.13%2Fwarp10-1.2.13.tar.gz"
val windowsWarp10URL = "https://bintray.com/cityzendata/generic/download_file?file_path=io%%2Fwarp10%%2Fwarp10%%2F1.2.13%%2Fwarp10-1.2.13.tar.gz"

def commonSettings(moduleName: String) = Seq(
  name := s"O-MI-$moduleName",
  version := "1.0.6-warp10", // WARN: Release ver must be "x.y.z" (no dashes, '-')
  scalaVersion := "2.12.6",
  scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint", s"-P:genjavadoc:out=${target.value}/java"),
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-deprecation", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8"),
  //javacOptions += "-Xlint:unchecked",
  autoAPIMappings := true,
  exportJars := true,
  Test / fork := true,
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
  enablePlugins(JavaAgent).//Kamon
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

//      bashScriptExtraDefines += """java io.warp10.word.Worf -a io.warp10.bootstrap -puidg -t -ttl 3153600000000 ${app_home}/../configs/conf-standalone.template -o ${app_home}/../configs/conf-standalone.conf >> ${app_home}/../configs/initial.tokens""",


    ////////////////////////////////////////////////////////////////////////////////////
    //additional lines to be added to start script to generate tokens for database and//
    //start the warp10 database before starting O-MI node.//////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////
      bashScriptExtraDefines += s"""WARP10_URL="$unixWarp10URL"""",
      bashScriptExtraDefines += """
declare java_cmd=$(get_java_cmd)
WARP10_HOME="${app_home}/../database/warp10"
WARP10_CONFIG="${WARP10_HOME}/etc/conf-standalone.conf"
WARP10_JAR="${WARP10_HOME}"/bin/warp10.jar
WARP10_CLASS=io.warp10.standalone.Warp
WARP10_INIT=io.warp10.standalone.WarpInit
WARP10_CP="${WARP10_JAR}"
WARP10_HEAP=512m
WARP10_HEAP_MAX=1g
WARP10_SENSISION_EVENTS_DIR="${WARP10_HOME}/data/sensision/data/metrics"
WARP10_LOG4J_CONF="${WARP10_HOME}/etc/log4j.properties"
WARP10_JAVA_HEAP_DUMP="${WARP10_HOME}/logs/java.heapdump"
WARP10_JAVA_OPTS="-Djava.awt.headless=true -Dlog4j.configuration=file:${WARP10_LOG4J_CONF} -Xms${WARP10_HEAP} -Xmx${WARP10_HEAP_MAX} -XX:+UseG1GC"
if [ ! -d "${WARP10_HOME}" ]; then
  "$java_cmd" -cp "${app_classpath}" DownloadBinaries "${WARP10_HOME}" "${WARP10_URL}"
fi
if [ ! -f "${WARP10_CONFIG}" ]; then
  "$java_cmd" -cp ${WARP10_JAR} io.warp10.worf.Worf -a io.warp10.bootstrap -puidg -t -ttl 3153600000000 ${WARP10_HOME}/templates/conf-standalone.template -o ${WARP10_HOME}/etc/conf-standalone.conf >> ${WARP10_HOME}/etc/initial.tokens
  "$java_cmd" -cp "${app_classpath}" ReplacePath "${WARP10_HOME}"
fi

LEVELDB_HOME="`${java_cmd} -Xms64m -Xmx64m -XX:+UseG1GC -cp ${WARP10_CP} io.warp10.WarpConfig ${WARP10_CONFIG} 'leveldb.home' | grep 'leveldb.home' | sed -e 's/^.*=//'`"

if [ ! -e ${LEVELDB_HOME} ]; then
  echo "${LEVELDB_HOME} does not exist - Creating it..."
  mkdir -p ${LEVELDB_HOME} 2>&1
  if [ $? != 0 ]; then
    echo "${LEVELDB_HOME} creation failed"
    exit 1
  fi
fi

if [ "$(find -L ${LEVELDB_HOME} -maxdepth 1 -type f | wc -l)" -eq 0 ]; then
  echo "Init leveldb"
  # Create leveldb database
  echo \"Init leveldb database...\" >> ${WARP10_HOME}/logs/warp10.log
  $java_cmd ${WARP10_JAVA_OPTS} -cp ${WARP10_CP} ${WARP10_INIT} ${LEVELDB_HOME} >> ${WARP10_HOME}/logs/warp10.log 2>&1
fi

if [ "`jps -lm|grep ${WARP10_CLASS}|cut -f 1 -d' '`" == "" ]
then
  "$java_cmd" "${WARP10_JAVA_OPTS}" -cp "${WARP10_CP}" "${WARP10_CLASS}" "${WARP10_CONFIG}" >> "${WARP10_HOME}/logs/warp10.log" 2>&1 &
else
  echo "A Warp 10 instance is already running"
fi
""",
      batScriptExtraDefines += s"""set "WARP10_URL=$windowsWarp10URL"""",
      batScriptExtraDefines += """set "WARP10_HOME=%O_MI_NODE_HOME%\database\warp10"""",
      batScriptExtraDefines += """set "WARP10_CONFIG=%WARP10_HOME%\etc\conf-standalone.conf"""",
      batScriptExtraDefines += """set "WARP10_JAR=%WARP10_HOME%\bin\warp10.jar"""",
      batScriptExtraDefines += """set "WARP10_INIT=io.warp10.standalone.WarpInit"""",
      batScriptExtraDefines += """set "WARP10_CLASS=io.warp10.standalone.Warp"""",
      batScriptExtraDefines += """set "WARP10_CP=%WARP10_JAR%"""",
      batScriptExtraDefines += """set "WARP10_HEAP=512m"""",
      batScriptExtraDefines += """set "WARP10_HEAP_MAX=1g"""",
      batScriptExtraDefines += """set "WARP10_SENSISION_EVENTS_DIR=%WARP10_HOME%\data\sensision\data\metrics"""",
      batScriptExtraDefines += """set "WARP10_LOG4J_CONF=%WARP10_HOME%\etc\log4j.properties"""",
      batScriptExtraDefines += """set "WARP10_JAVA_HEAP_DUMP=%WARP10_HOME%\logs\java.heapdump"""",
      batScriptExtraDefines += """set "WARP10_JAVA_OPTS=-Djava.awt.headless=true -Dlog4j.configuration=file:%WARP10_LOG4J_CONF% -Xms%WARP10_HEAP% -Xmx%WARP10_HEAP_MAX% -XX:+UseG1GC"""",
      batScriptExtraDefines += """set "FINDSTR_COMMAND=%SystemRoot%\\System32\\findstr.exe"""",
      batScriptExtraDefines += """set "JPS_CMD=jps.exe"""",
      batScriptExtraDefines += """set "JPS_OK=false"""",
      batScriptExtraDefines += """if not "%JAVA_HOME%"=="" (""",
      batScriptExtraDefines += """  if exist "%JAVA_HOME%\bin\jps.exe" set "JPS_CMD=%JAVA_HOME%\bin\jps.exe"""",
      batScriptExtraDefines += """)""",
      batScriptExtraDefines += """for /f "tokens=2" %%t in ('"%JPS_CMD%" 2^>^&1') do (""",
      batScriptExtraDefines += """  if %%~t==Jps set JPS_OK=true""",
      batScriptExtraDefines += """)""",
      batScriptExtraDefines += """if not exist %WARP10_JAR% (""",
      batScriptExtraDefines += """  "%_JAVACMD%" -cp "%APP_CLASSPATH%" DownloadBinaries "%WARP10_HOME%" "%WARP10_URL%"""",
      batScriptExtraDefines += """)""",
      batScriptExtraDefines += """""",
      batScriptExtraDefines += """if not exist %WARP10_CONFIG% (""",
      batScriptExtraDefines += """  "%_JAVACMD%" -cp %WARP10_JAR% io.warp10.worf.Worf -a io.warp10.bootstrap -puidg -t -ttl 3153600000000 "%WARP10_HOME%/templates/conf-standalone.template" -o "%WARP10_HOME%/etc/conf-standalone.conf" >> "%WARP10_HOME%\\etc\\initial.tokens"""",
      batScriptExtraDefines += """  "%_JAVACMD%" -cp "%APP_CLASSPATH%" ReplacePath "%WARP10_HOME%"""",
      batScriptExtraDefines += """)""",
      batScriptExtraDefines += """""",
      batScriptExtraDefines += """>nul 2>nul dir /a-d "%WARP10_HOME%\leveldb\*" """,
      batScriptExtraDefines += """if %ERRORLEVEL% NEQ 0 (""",
      batScriptExtraDefines += """echo Initializing leveldb""",
      batScriptExtraDefines += """echo "Init leveldb database..." >> "%WARP10_HOME%\\logs\\warp10.log"""",
      batScriptExtraDefines += """  "%_JAVACMD% -Xms64m -Xmx64m -XX:+UseG1GC -cp "%WARP10_CP%" io.warp10.WarpConfig "%WARP10_CONFIG%" 'leveldb.home' """",
      batScriptExtraDefines += """  mkdir "%WARP10_HOME%\leveldb"""",
      batScriptExtraDefines += """  "%_JAVACMD%" -cp "%WARP10_JAR%" "%WARP10_INIT%" "%WARP10_HOME%/leveldb" >> "%WARP10_HOME%\\logs\\warp10.log" 2>&1""",
      batScriptExtraDefines += """)""",
      batScriptExtraDefines += """if "%JPS_OK%"=="true" (""",
      //batScriptExtraDefines += """"%JPS_CMD%" -l | "%FINDSTR_COMMAND%" %WARP10_CLASS%""",
      //batScriptExtraDefines += """if %ERRORLEVEL% gtr 0 (""",
      batScriptExtraDefines += """  start "warp10" "%_JAVACMD%" !WARP10_JAVA_OPTS! -cp "!WARP10_CP!" !WARP10_CLASS! "!WARP10_CONFIG!" ^>^> "!WARP10_HOME!\\logs\\warp10.log" ^2^>^&^1""",
      //batScriptExtraDefines += """) else (""",
      //batScriptExtraDefines += """  echo Warp10 is already running!""",
      //batScriptExtraDefines += """  )""",
      batScriptExtraDefines += """) else (""",
      batScriptExtraDefines += """  echo A Java JDK is not installed or can't be found. jps.exe was not found""",
      batScriptExtraDefines += """)""",
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
      /**
       * TODO: Start using after sorting out possible issues and checking
       * that structure is in wanted format. Should solve issues with wrong
       * permissions preventing creating files.
       */
    /*
    //Create empty database directory for Tar. Zip removes empty directories?
    //TODO: Check Warp10
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
            s"/var/lib/${normalizedName.value}/journal"
          )() withUser( daemonUser.value ) withGroup( daemonGroup.value ),
          packageTemplateMapping(
            s"/var/lib/${normalizedName.value}/snapshots"
          )() withUser( daemonUser.value ) withGroup( daemonGroup.value )
        ),
      linuxPackageSymlinks ++=Seq(
        LinuxSymlink( s"/usr/share/${normalizedName.value}/database", s"/var/lib/${normalizedName.value}/database"),
        LinuxSymlink( s"/usr/share/${normalizedName.value}/journal", s"/var/lib/${normalizedName.value}/journal"),
        LinuxSymlink( s"/usr/share/${normalizedName.value}/snapshost", s"/var/lib/${normalizedName.value}/snapshost")
      ),
    */
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
  
