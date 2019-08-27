import sbt._
import Keys._

object Dependencies {

  //Akka 
  val akkaV = "2.6.0-M2" // "2.6-SNAPSHOT" //"2.5.23"

  val akkaHttpV = "10.1.8"
  val akkaActor       = "com.typesafe.akka" %% "akka-actor" % akkaV //
  val akkaSlf4j       = "com.typesafe.akka" %% "akka-slf4j" % akkaV
  val akkaStream      = "com.typesafe.akka" %% "akka-stream" % akkaV
  val akkaPersistance = "com.typesafe.akka" %% "akka-persistence" % akkaV


  val http         = "com.typesafe.akka" %% "akka-http-core" % akkaHttpV
  val httpExperimnt= "com.typesafe.akka" %% "akka-http" % akkaHttpV
  val httpXml      = "com.typesafe.akka" %% "akka-http-xml" % akkaHttpV
  val sprayJson    = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV 
  val httpCors     = "ch.megard"         %% "akka-http-cors" % "0.4.1"
  //Test dependencies
  val specs2V = "4.3.0"
  val specs2       = "org.specs2"        %% "specs2-core"   % specs2V   % "test"
  val specs2match  = "org.specs2"        %% "specs2-matcher-extra" % specs2V % "test"
  val mockito	     = "org.specs2"        %% "specs2-mock"   % specs2V   % "test"
  val nuValidator  = "nu.validator"      % "htmlparser"     % "1.4.3"   % "test" //html5 parser for systemtests
  val akkaTestkit  = "com.typesafe.akka" %% "akka-testkit"  % akkaV     % "test"
  val httpTestkit  = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"

  //Slick
  val slickV = "3.2.3"
  val slick        = "com.typesafe.slick" %% "slick" % slickV //common
  val slickCodegen = "com.typesafe.slick" %% "slick-codegen"  % slickV //common
  val hikariCP     = "com.typesafe.slick" %% "slick-hikaricp" % slickV
  //val sqliteJdbc   = "org.xerial"          % "sqlite-jdbc"    % "3.7.2" //common
  //"com.zaxxer"          % "HikariCP-java6" % "2.3.3" // XXX: manually updated dependency, slick had 2.0.1
  val h2           = "com.h2database"      % "h2"             % "1.4.197" //common
  val leveldb      = "org.iq80.leveldb"    % "leveldb"        % "0.10"
  val leveldbjni   = "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
  //val json4s       = "org.json4s"         %% "json4s-native"  % "3.3.0" //common
  val postgres     = "org.postgresql"      % "postgresql"      % "9.4.1211"
  val json4s       = "org.json4s"         %% "json4s-native"   % "3.6.6" //common
  val json4sAkka   = "de.heikoseeberger"  %% "akka-http-json4s" % "1.26.0" //common

  //etc
  val logback          = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val scalaProto       = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"

  val redis = "com.safety-data" %% "akka-persistence-redis" % "0.4.1"
  val alpakka = "com.lightbend.akka" %% "akka-stream-alpakka-xml" % "1.0.0"


  val prometheus = Seq(
    "io.prometheus" % "simpleclient" % "0.6.0",
    "io.prometheus" % "simpleclient_hotspot" % "0.6.0",
    "io.prometheus" % "simpleclient_httpserver" % "0.6.0",
    "io.prometheus" % "simpleclient_pushgateway" % "0.6.0",
  )


  //val schwatcher   = "com.beachape.filemanagement" %% "schwatcher"   % "0.3.1" //common
  //val commonsLang  = "org.apache.commons" % "commons-lang3" % "3.7"

  //Scala XML      
  //val scalaXML     = "org.scala-lang.modules"      %% "scala-xml"    % "2.11.0-M4"

  //STM
  val stm          = "org.scala-stm"               %% "scala-stm"    % "0.8"

  //Java dependencies
  val gson         = "com.google.code.gson"         % "gson"         % "2.8.5"

  
    val commonDependencies: Seq[ModuleID] = Seq(
    akkaActor,
    akkaSlf4j,
    akkaStream,
    akkaPersistance,
    logback,
    http,
    httpExperimnt,
    httpCors,
    httpXml,
    slick,
    slickCodegen,
    //sqliteJdbc,
    hikariCP,
    h2,
    postgres,
    leveldb,
    leveldbjni,
    stm,
    sprayJson,
    json4s,
    json4sAkka,
    //scalaXML,
    //commonsLang,
    scalaProto,
    gson,
    redis,
    alpakka
  ) ++ prometheus

  //val servletDependencies: Seq[ModuleID] = Seq(
  //  sprayServlet,
  //  stm)

  val testDependencies: Seq[ModuleID] = Seq(
    specs2,
    specs2match,
    mockito,
    nuValidator,
    akkaTestkit,
    httpTestkit
  )

}
