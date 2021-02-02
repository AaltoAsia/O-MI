// https://github.com/spray/sbt-revolver
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

// https://github.com/sbt/sbteclipse
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")

// https://github.com/scoverage/sbt-scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

// https://github.com/scoverage/sbt-coveralls
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.5")

// https://github.com/thesamet/sbt-protoc
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.18")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.7.4"

// https://github.com/scalastyle/scalastyle-sbt-plugin
// http://www.scalastyle.org/sbt.html
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"


// https://github.com/codacy/sbt-codacy-coverage
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.12")
 
resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

resolvers += "Sonatype OSS Snapshots" at
  "https://oss.sonatype.org/content/repositories/snapshots"

// https://github.com/earldouglas/xsbt-web-plugin
addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "4.0.2")

// https://github.com/sbt/sbt-native-packager
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6")

// https://github.com/jrudolph/sbt-dependency-graph
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.1")

// https://github.com/kamon-io/sbt-aspectj-runner
resolvers += Resolver.bintrayRepo("kamon-io", "sbt-plugins")
addSbtPlugin("io.kamon" % "sbt-aspectj-runner" % "1.1.0")

// TODO: To check: Is this used? if not, remove
// https://github.com/sbt/sbt-javaagent
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")

//https://github.com/sbt/sbt-unidoc
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.3")

