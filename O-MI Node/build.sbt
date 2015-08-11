//import com.github.retronym.SbtOneJar
//import Dependencies._

name := "O-MI-Node"

cleanFiles <++= baseDirectory {_ * "*.db" get}

oneJarSettings

Revolver.settings



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

