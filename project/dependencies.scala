import sbt._
import Keys._

object Dependencies {

  //Akka 
  val akkaV = "2.4.7"
  val akkaActor    = "com.typesafe.akka" %% "akka-actor" % akkaV //
  val akkaSlf4j    = "com.typesafe.akka" %% "akka-slf4j" % akkaV

  //Spray
  //val sprayV = "1.3.3"
  val http         = "com.typesafe.akka" %% "akka-http-core" % akkaV
  val httpExperimnt= "com.typesafe.akka" %% "akka-http-experimental" % akkaV

  //Test dependencies
  val specs2       = "org.specs2"           %% "specs2-core"   % "3.7.2"   % "test"
  val specs2match  = "org.specs2"           %% "specs2-matcher-extra" % "3.7.2" % "test"
  val mockito	   = "org.specs2"             %% "specs2-mock"   % "3.7.2"   % "test"
  val nuValidator  = "nu.validator.htmlparser" % "htmlparser"  % "1.4"     % "test" //html5 parser for systemtests
  val akkaTestkit  = "com.typesafe.akka"    %% "akka-testkit"  % akkaV     % "test"
  val httpTestkit  = "com.typesafe.akka"    %% "akka-http-testkit" % akkaV % "test"

  //Slick
  val slickV = "3.1.1"
  val slick        = "com.typesafe.slick" %% "slick" % slickV //common
  val slickCodegen = "com.typesafe.slick" %% "slick-codegen"  % slickV //common
  val hikariCP     = "com.typesafe.slick" %% "slick-hikaricp" % slickV
  val slf4jNop     = "org.slf4j"           % "slf4j-nop"      % "1.6.4" //common
  val sqliteJdbc   = "org.xerial"          % "sqlite-jdbc"    % "3.7.2" //common
//"com.zaxxer"          % "HikariCP-java6" % "2.3.3" // XXX: manually updated dependency, slick had 2.0.1
  val h2           = "com.h2database"      % "h2"             % "1.4.192" //common
  val json4s       = "org.json4s"         %% "json4s-native"  % "3.2.11" //common

  //etc
  val logback = "ch.qos.logback" % "logback-classic" % "1.0.13"
  val prevaylerCore = "org.prevayler" % "prevayler-core" % "2.6"
  val prevaylerFactory = "org.prevayler" % "prevayler-factory" % "2.6"




  val schwatcher   = "com.beachape.filemanagement" %% "schwatcher"   % "0.1.8" //common
  val commonsLang  = "commons-lang"                 % "commons-lang" % "2.6" //common

  //Scala XML      
  val scalaXML     = "org.scala-lang.modules"      %% "scala-xml"    % "1.0.4" //

  //STM            
  val stm          = "org.scala-stm"               %% "scala-stm"    % "0.7"

  //Java dependencies
  val gson         = "com.google.code.gson"         % "gson"         % "2.5"

  val commonDependencies: Seq[ModuleID] = Seq(
    akkaActor,
    akkaSlf4j,
    logback,
    http,
    httpExperimnt,
    slick,
    slickCodegen,
    slf4jNop,
    sqliteJdbc,
    hikariCP,
    h2,
    json4s,
    scalaXML,
    schwatcher,
    commonsLang,
    prevaylerCore,
    prevaylerFactory,
    gson)

  //val servletDependencies: Seq[ModuleID] = Seq(
  //  sprayServlet,
  //  stm)

  val testDependencies: Seq[ModuleID] = Seq(
    specs2,
    specs2match,
    mockito,
    nuValidator,
    akkaTestkit,
    httpTestkit)

}
