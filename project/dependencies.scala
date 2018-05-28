import sbt._
import Keys._

object Dependencies {

  //Akka 
  val akkaV = "2.5.12"
  val akkaHttpV = "10.0.11"
  val akkaActor    = "com.typesafe.akka" %% "akka-actor" % akkaV //
  val akkaSlf4j    = "com.typesafe.akka" %% "akka-slf4j" % akkaV
  val akkaStream   = "com.typesafe.akka" %% "akka-stream" % akkaV
  val akkaPersistance = "com.typesafe.akka" %% "akka-persistence" % akkaV

  val http         = "com.typesafe.akka" %% "akka-http-core" % akkaHttpV
  val httpExperimnt= "com.typesafe.akka" %% "akka-http" % akkaHttpV
  val httpXml      = "com.typesafe.akka" %% "akka-http-xml" % akkaHttpV
  val sprayJson    = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV 
  val httpCors     = "ch.megard"         %% "akka-http-cors" % "0.2.2"
  //Test dependencies
  val specs2V = "3.7.2"
  val specs2       = "org.specs2"        %% "specs2-core"   % specs2V   % "test"
  val specs2match  = "org.specs2"        %% "specs2-matcher-extra" % specs2V % "test"
  val mockito	     = "org.specs2"        %% "specs2-mock"   % specs2V   % "test"
  val nuValidator  = "nu.validator"      % "htmlparser"     % "1.4.3"   % "test" //html5 parser for systemtests
  val akkaTestkit  = "com.typesafe.akka" %% "akka-testkit"  % akkaV     % "test"
  val httpTestkit  = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"

  //Slick
  val slickV = "3.1.1"
  val slick        = "com.typesafe.slick" %% "slick" % slickV //common
  val slickCodegen = "com.typesafe.slick" %% "slick-codegen"  % slickV //common
  val hikariCP     = "com.typesafe.slick" %% "slick-hikaricp" % slickV
  //val sqliteJdbc   = "org.xerial"          % "sqlite-jdbc"    % "3.7.2" //common
  //"com.zaxxer"          % "HikariCP-java6" % "2.3.3" // XXX: manually updated dependency, slick had 2.0.1
  val h2           = "com.h2database"      % "h2"             % "1.4.192" //common
  val postgres     = "org.postgresql"      % "postgresql"      % "9.4.1211"
  //val json4s       = "org.json4s"         %% "json4s-native"  % "3.3.0" //common

  //etc
  val logback          = "ch.qos.logback" % "logback-classic" % "1.1.3"
  val prevaylerV = "2.6"
  val prevaylerCore    = "org.prevayler"  % "prevayler-core"   % prevaylerV
  val prevaylerFactory = "org.prevayler"  % "prevayler-factory"% prevaylerV
  val scalameter = "com.storm-enroute" %% "scalameter" % "0.7"




  //val schwatcher   = "com.beachape.filemanagement" %% "schwatcher"   % "0.3.1" //common
  val commonsLang  = "commons-lang"                 % "commons-lang" % "2.6" //common

  //Scala XML      
  //val scalaXML     = "org.scala-lang.modules"      %% "scala-xml"    % "2.11.0-M4"

  //STM            
  val stm          = "org.scala-stm"               %% "scala-stm"    % "0.8"

  //Java dependencies
  val gson         = "com.google.code.gson"         % "gson"         % "2.6.2"
  
  //Monitoring with Kamon
  val kamon = "io.kamon" %% "kamon-core" % "1.1.0"
  val kamonInfluxReporter = "io.kamon" %% "kamon-influxdb" % "1.0.1"
  val kamonAkka = "io.kamon" %% "kamon-akka-2.4" % "1.0.1" 
  val kamonAkkaHTTP = "io.kamon" %% "kamon-akka-http-2.4" % "1.0.1" 
  val kamonLogback = "io.kamon" %% "kamon-logback" % "1.0.0"
  val kamonPrometheus = "io.kamon" %% "kamon-prometheus" % "1.0.0"

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
    stm,
    sprayJson,//json4s,
    //scalaXML,
    commonsLang,
    prevaylerCore,
    prevaylerFactory,
    gson,
    kamon,
    kamonLogback,
    kamonAkka,
    kamonPrometheus,
    kamonInfluxReporter
  )

  //val servletDependencies: Seq[ModuleID] = Seq(
  //  sprayServlet,
  //  stm)

  val testDependencies: Seq[ModuleID] = Seq(
    specs2,
    specs2match,
    mockito,
    nuValidator,
    akkaTestkit,
    httpTestkit,
    scalameter
  )

}
