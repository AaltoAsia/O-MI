import com.github.retronym.SbtOneJar

val scalaBuildVersion = "2.11.4"

//lazy val root = (project in file(".")).
name := "O-MI-Node"

version := "0.1.1-SNAPSHOT"

scalaVersion := scalaBuildVersion

// build options
scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-Xlint")

// build api options
scalacOptions in (Compile,doc) ++= Seq("-groups", "-implicits", "-diagrams", "-diagrams-debug", "-encoding", "utf8")

autoAPIMappings := true 

// STM
//libraryDependencies += ("org.scala-stm" %% "scala-stm" % "0.7")

// SPRAY
libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "io.spray"			      %%  "spray-client"  % sprayV,
    "io.spray"			      %%  "spray-servlet" % sprayV,
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "2.3.11" % "test",
    "org.json4s" 		      %%  "json4s-native" % "3.2.11"
    )
}

libraryDependencies += "com.beachape.filemanagement" %% "schwatcher" % "0.1.8"

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

//slick
libraryDependencies ++= Seq(
"com.typesafe.slick"  %%  "slick" % "3.0.0",
"com.typesafe.slick" %% "slick-codegen" % "3.0.0",
"org.slf4j" % "slf4j-nop" % "1.6.4",
"org.xerial" % "sqlite-jdbc" % "3.7.2",
"com.zaxxer" % "HikariCP-java6" % "2.3.3", // XXX: manually updated dependency, slick had 2.0.1
"com.h2database" % "h2" % "1.4.187"
)

//remove database files
cleanFiles <++= baseDirectory {_ * "*.db" get}

oneJarSettings

Revolver.settings

// Eclipse
EclipseKeys.withSource := true

// We have common database in some tests
parallelExecution in Test := false


////////////////////////////
// Make a .zip release file

val releaseDir = SettingKey[File]("releaseDirectory", "Directory for jar and zip packages")

// directory for zip file
releaseDir <<= (baseDirectory) { _ / "release" }


// change one-jar filename template
artifactName in oneJar := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
  artifact.name + "-" + module.revision + "." + artifact.extension
}

// custom "release" sbt command
val release = TaskKey[File]("release", "build jar package and zip it")

// change .zip file name template
artifactName in release := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
  artifact.name + "-" + module.revision + "." + artifact.extension
}

// didn't work:
//addArtifact(Artifact("o-mi-node", "zip", "zip"), release)

// .zip base file name and path
inTask(release)(Seq(
  artifact := Artifact("o-mi-node", "zip", "zip"),
  artifactPath <<= Defaults.artifactPathSetting(artifact)
))


// TODO: move to .scala file, currently no empty lines are allowed
release <<= (resourceDirectory in Compile, releaseDir, baseDirectory, artifact, artifactPath in release, oneJar)  map {
  case (resourceDir, releaseDir, baseDirectory, art, defPath, jar) =>
    val resultPath = releaseDir / defPath.getName()
    //
    // Files to put in release (path: File -> path_in_zip: String)
    var zipFileList = 
      Seq(
        resourceDir / "application.conf" -> "application.conf",
        baseDirectory / "start.sh" -> "start.sh",
        baseDirectory / "start.bat" -> "start.bat",
        baseDirectory / ".." / "callbackTestServer.py" -> "callbackTestServer.py",
        baseDirectory / "SmartHouse.xml" -> "SmartHouse.xml",
        baseDirectory / "otaniemi3d-data.xml" -> "otaniemi3d-data.xml",
        baseDirectory / "README-release.md" -> "README.md",
        jar -> jar.getName()
      )
    val directories = Seq("configs", "deploy", "html")
    //
    directories foreach { dir =>
      val paths = Path.allSubpaths(baseDirectory / dir)
      val pathsWithoutVimBac = paths filter {_._1.toString.last != '~'}
      zipFileList ++=
        pathsWithoutVimBac map {case (path, relative) => path -> (dir + "/" + relative)}
    }
    println(zipFileList.mkString(",\n"))
    IO.zip(zipFileList, resultPath)
    println(s"Finished: $resultPath with jar file from: $jar")
    resultPath
}

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := "parsing.xmlGen.*;"

// Choose Tomcat or Jetty default settings and build a .war file with `sbt package`
tomcat() 
// jetty()

