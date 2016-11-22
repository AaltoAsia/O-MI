import com.github.retronym.SbtOneJar
import Dependencies._
import NativePackagerHelper._
import Path.relativeTo
import com.typesafe.sbt.packager.archetypes.ServerLoader.{SystemV,Upstart}

lazy val separator = taskKey[Unit]("Prints seperating string")
separator := println("########################################################\n\n\n\n")

addCommandAlias("release", ";doc;universal:packageBin;universal:packageZipTarball")
addCommandAlias("systemTest", "omiNode/testOnly http.SystemTest")


def commonSettings(moduleName: String) = Seq(
  name := s"O-MI-$moduleName",
  version := "0.7.0-warp10",
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
      cleanFiles <+= baseDirectory { base => base / "logs"},
      //cleanFiles <++= baseDirectory {_ * "*.db" get},
      target in (Compile, doc) := baseDirectory.value / "html" / "api",
      target in (JavaDoc, doc) := baseDirectory.value / "html" / "api" / "java",
      //Revolver.settings,
      libraryDependencies ++= commonDependencies ++ testDependencies)): _*) //  ++ servletDependencies

lazy val agents = (project in file("Agents")).
  settings(commonSettings("Agents"): _*).
  settings(Seq(
    libraryDependencies ++= commonDependencies,
    crossTarget <<= (unmanagedBase in omiNode)
    )).
    dependsOn(omiNode)

lazy val root = (project in file(".")).
  enablePlugins(JavaServerAppPackaging).
  enablePlugins(DockerPlugin).
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
      cleanFiles <++= (baseDirectory in omiNode) {base => Seq(
        base / "html" / "api",
        base / "lib",
        base / "logs",
        file("logs"))},
    
    ////////////////////////////////////////////////////////////////////////
    //Update version file so that the web browser displays current version//
    ////////////////////////////////////////////////////////////////////////
      resourceGenerators in Compile <+= (baseDirectory in Compile in omiNode, version) map { (dir, currentVersion) =>
        val file = dir / "html" / "VERSION"
        IO.write(file, s"${currentVersion}")
        Seq(file)},

//      bashScriptExtraDefines += """java io.warp10.word.Worf -a io.warp10.bootstrap -puidg -t -ttl 3153600000000 ${app_home}/../configs/conf-standalone.template -o ${app_home}/../configs/conf-standalone.conf >> ${app_home}/../configs/initial.tokens""",


    ////////////////////////////////////////////////////////////////////////////////////
    //additional lines to be added to start script to generate tokens for database and//
    //start the warp10 database before starting O-MI node.//////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////
      bashScriptExtraDefines += """
declare java_cmd=$(get_java_cmd)
WARP10_URL="https://bintray.com/cityzendata/generic/download_file?file_path=io%2Fwarp10%2Fwarp10%2F1.1.0%2Fwarp10-1.1.0.tar.gz"
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

if [ "$(find ${WARP10_HOME}/data -maxdepth 1 -type f -printf 1 | wc -m)" -eq 0 ]; then
  echo "Init leveldb"
  # Create leveldb database
  echo "Init leveldb database..." >> "${WARP10_HOME}/logs/nohup.out"
   "$java_cmd" -cp "${WARP10_CP}" "${WARP10_INIT}" "${WARP10_HOME}/data" >> "${WARP10_HOME}/logs/nohup.out" 2>&1
fi

if [ "`jps -lm|grep ${WARP10_CLASS}|cut -f 1 -d' '`" == "" ]
then
  "$java_cmd" "${WARP10_JAVA_OPTS}" -cp "${WARP10_CP}" "${WARP10_CLASS}" "${WARP10_CONFIG}" >> "${WARP10_HOME}/logs/nohup.out" 2>&1 &
else
  echo "A Warp 10 instance is already running"
fi
""",
      batScriptExtraDefines += """set "WARP10_URL=https://dl.bintray.com/cityzendata/generic/io/warp10/warp10/1.0.7/warp10-1.0.7.gz"""",
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
      batScriptExtraDefines += """>nul 2>nul dir /a-d "%WARP10_HOME%\data\*" """,
      batScriptExtraDefines += """if %ERRORLEVEL% NEQ 0 (""",
      batScriptExtraDefines += """echo Initializing leveldb""",
      batScriptExtraDefines += """echo "Init leveldb database..." >> "%WARP10_HOME%\\logs\\nohup.out"""",
      batScriptExtraDefines += """  "%_JAVACMD%" -cp "%WARP10_JAR%" "%WARP10_INIT%" "%WARP10_HOME%/data" >> "%WARP10_HOME%\\logs\\nohup.out" 2>&1""",
      batScriptExtraDefines += """)""",
      batScriptExtraDefines += """if "%JPS_OK%"=="true" (""",
      batScriptExtraDefines += """"%JPS_CMD%" -l | "%FINDSTR_COMMAND%" %WARP10_CLASS%""",
      batScriptExtraDefines += """if %ERRORLEVEL% gtr 0 (""",
      batScriptExtraDefines += """  start "warp10" "%_JAVACMD%" !WARP10_JAVA_OPTS! -cp "!WARP10_CP!" !WARP10_CLASS! "!WARP10_CONFIG!" ^>^> "!WARP10_HOME!\\logs\\nohup.out" ^2^>^&^1""",
      batScriptExtraDefines += """) else (""",
      batScriptExtraDefines += """  echo Warp10 is already running!""",
      batScriptExtraDefines += """  )""",
      batScriptExtraDefines += """) else (""",
      batScriptExtraDefines += """  echo A Java JDK is not installed or can't be found. jps.exe was not found""",
      batScriptExtraDefines += """)""",
    ///////////////////////////////////////////////////////////////////////
    //Configure program to read application.conf from the right direction//
    ///////////////////////////////////////////////////////////////////////

      bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../configs/application.conf"""",
      bashScriptExtraDefines += """cd  ${app_home}/..""",
      batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dconfig.file="%O_MI_NODE_HOME%\\configs\\application.conf"""", 
      batScriptExtraDefines += """cd "%~dp0\.."""",

    ////////////////////////////
    //Native packager settings//
    ////////////////////////////
      serverLoading in Debian := SystemV,
    //Mappings tells the plugin which files to include in package and in what directory
      mappings in Universal <++= (baseDirectory in omiNode) map (src => directory(src / "html")),
      mappings in Universal <++= baseDirectory map (src => directory(src / "configs")),
      mappings in Universal <+= (packageBin in Compile, sourceDirectory in omiNode) map { (_, src) =>
        val conf = src / "main" / "resources" / "application.conf"
        conf -> "configs/application.conf"},
      mappings in Universal <++= (doc in Compile in omiNode, baseDirectory in omiNode) map { (_, base) =>
        directory(base / "html" / "api").map(n => (n._1, "html/" + n._2))},
      mappings in Universal <++= baseDirectory map { base =>
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

  
