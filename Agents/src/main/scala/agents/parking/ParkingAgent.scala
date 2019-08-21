package agents.parking

import java.io.File
import java.sql.Timestamp

import agentSystem._
import agents.parking.UserGroup._
import agents.parking.VehicleType._
import akka.actor.{ActorRef, Props}
import com.typesafe.config.Config
import types.omi._
import types.Path._
import types._
import types.odf._
import types.odf.parsing.ODFStreamParser
import akka.stream.scaladsl.FileIO
import akka.stream.ActorMaterializer

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.xml.XML
import utils._

object ParkingAgent extends PropsCreator{
  /**
   * Method for creating Props for ResponsibleScalaAgent.
   *  @param config Contains configuration for this agent, as given in application.conf.
   */
  def props(
    config: Config,
    requestHandler: ActorRef, 
    dbHandler: ActorRef
  ) : Props = Props( new ParkingAgent(config, requestHandler, dbHandler) )
}

class ParkingAgent(
  val config: Config,
  val requestHandler: ActorRef, 
  val dbHandler: ActorRef
) extends ResponsibleScalaInternalAgent{

  import context.dispatcher
  implicit val m = ActorMaterializer()

  def configStringToPath( name: String ): Path ={
    Path(config.getString(name))
  }
  //Base path for service, contains at least all method
  val servicePath: Path = configStringToPath("servicePath")
  val parkingFacilitiesPath: Path = configStringToPath("parkingFacilitiesPath")
  val findParkingPath: Path = servicePath / "FindParking"
  val calculateCapacitiesEnabled: Boolean = if( config.hasPath("calculate-capacities") ) config.getBoolean("calculate-capacities") else false

  //Path to object containing all parking lots.
  val latitudePFIndex: mutable.SortedMap[ Double, Set[Path] ] = mutable.SortedMap.empty
  val longitudePFIndex: mutable.SortedMap[ Double, Set[Path] ] = mutable.SortedMap.empty

  val prefixes: mutable.Map[String,Set[String]] = mutable.Map(
    "http://www.schema.org/" -> Set("schema:","sdo:"),
    "http://www.schema.mobivoc.org/" -> Set("mv:")
  )
  val tmprefixes = Map(
    "http://www.schema.org/" -> "schema:",
    "http://www.schema.mobivoc.org/" -> "mv:"
  )

  //File used to populate node with initial state
  {
    val startStatePath = java.nio.file.Paths.get(config.getString("initialStateFile"))
    val startStateFile = startStatePath.toFile
    val initialODF: ImmutableODF = if( startStateFile.exists() && startStateFile.canRead() ){
      val f = ODFStreamParser.byteStringParser( FileIO.fromPath(startStatePath) ) transform {
        case Failure(errors) =>
          log.warning(s"Odf has errors, $name could not be configured.")
          log.info(errors.toString)
          Failure(AgentConfigurationException(s"File $startStateFile could not be parsed, because of following errors: $errors"))
        case Success(odf: ODF) => Success(odf.toImmutable)
      }
      Await.result(f, 5.minute)
    } else if( !startStateFile.exists() ){
      throw AgentConfigurationException(s"Could not get initial state for $name. File $startStateFile do not exists.")
    } else {
      throw  AgentConfigurationException(s"Could not get initial state for $name. Could not read file $startStateFile.")
    }
    
    updatePrefixes(initialODF)
    val initialWrite: Future[ResponseRequest] = writeToDB( WriteRequest(initialODF,ttl = 10.minutes) ).flatMap{
      writeResponse: ResponseRequest =>
        val wfails = writeResponse.results.filterNot{
          result => result.returnValue.returnCode.startsWith("2")
        }
        if( wfails.nonEmpty) Future.successful{ writeResponse }
        else{
          readFromDB(ReadRequest(odf = ImmutableODF(Seq(Object(servicePath))), ttl = 10.minutes)).map{
            response: ResponseRequest => 
              val (success,fails) = response.results.partition{
                result => result.returnValue.returnCode.startsWith("2")
              }
              if( fails.nonEmpty) Future.successful{ response }
              else{
                success.foreach {
                  result: OmiResult =>
                    result.odf.map{
                      odf =>
                        updatePrefixes(odf)
                        setPrefixes(odf)
                    
                    }.foreach {
                      odf =>
                        updateIndexesAndCapacities(odf.toImmutable, response)
                    }
                }
              }
              response
          }
        }
    }
    initialWrite.recover{
      case e: Exception => 
        throw new Exception(s"Could not set initial state for $name. Initial write to DB failed.", e)
    }.map{
      response: ResponseRequest =>
        response.results.foreach {
          result: OmiResult =>
            result.returnValue match {
              case succ: Returns.ReturnTypes.Successful =>
                log.info(s"Successfully initialized state for $name")
              case other =>
                log.warning(s"Could not set initial state for $name. Got result:$result.")
                throw new Exception(s"Could not set initial state for $name. Got following result from DB: $result.")
            }
        }
    }
  }
  def updateIndexes(pfs:Set[Object], odf: ODF): Unit ={
    val newPfs= pfs.filter{
      obj: Object => 
        !latitudePFIndex.values.flatten.toSet.contains(obj.path) && 
        !longitudePFIndex.values.flatten.toSet.contains(obj.path)
    }
    if( newPfs.size > 0 ){
      log.info( s"Found ${newPfs.size} new PFs to be added to indexes" )
      newPfs.foreach{
        node: Object =>
          log.debug( s"Create Geo ${node.path}" )
          val gps: Option[GeoCoordinates] = for{
            lat <- getDoubleOption("latitude", node.path / "geo",odf.toImmutable )
            lon <- getDoubleOption("longitude", node.path / "geo",odf.toImmutable )
          } yield GeoCoordinates( lat, lon )
          gps.foreach{
            gc =>
              log.debug( s"Adding ${node.path} to Geo indexes" )
              latitudePFIndex.get( gc.latitude ) match{
                case Some( paths: Set[Path]) => 
                  latitudePFIndex.update(gc.latitude,paths ++ Set(node.path) )
                case None =>
                  latitudePFIndex += gc.latitude -> Set(node.path)
              }
              longitudePFIndex.get( gc.longitude ) match{
                case Some( paths: Set[Path]) => 
                  longitudePFIndex.update(gc.longitude,paths ++ Set(node.path) )
                case None =>
                  longitudePFIndex += gc.longitude -> Set(node.path)
              }
          }
      }
      log.info( s"Current latitude index has: ${latitudePFIndex.values.map(_.size).sum} parking facilities")
      log.info( s"Current longitude index has: ${longitudePFIndex.values.map(_.size).sum} parking facilities")
    }
  }
  def updateIndexesAndCapacities(odf: ImmutableODF, response: ResponseRequest): Future[ResponseRequest]={
    val facilities = odf.nodesWithType(ParkingFacility.mvType(Some("mv:"))).collect{ case obj: Object => obj}
    updateIndexes(facilities,odf)
    val availableWrite = odf.nodesWithType(ParkingSpace.mvType(Some("mv"))).exists{
      obj: Node =>
        odf.get(obj.path / "available").nonEmpty ||
        odf.get(obj.path / "isOccupied").nonEmpty // isOccupied !
    }
    if( calculateCapacitiesEnabled && availableWrite && facilities.nonEmpty ) calculateCapacities(facilities.map(_.path))
    else Future{ response }
  }
  override def handleWrite( write: WriteRequest):Future[ResponseRequest] = {
    log.info( s"Write received")
    if( write.odf.get(findParkingPath).nonEmpty ){
      return  Future.successful(
        Responses.InvalidRequest(Some(s"Writing to $findParkingPath is not allowed.")) 
      )
    }
    Try{
      updatePrefixes(write.odf)
    }.failed.foreach{
      case NonFatal(e) => 
       return Future.successful(Responses.InvalidRequest(Some(s"Prefix upadate failed with $e")))
    }
    val wodf = setPrefixes(write.odf)

    {
      val facilities = wodf.getChilds( parkingFacilitiesPath)
      if( facilities.isEmpty ||
        facilities.forall{
          case node: Object => node.typeAttribute.isEmpty || !node.typeAttribute.contains(ParkingFacility.mvType(Some("mv:")))
          case node: InfoItem => true
          case node: Objects => throw new Exception("Wrong node type encountered in parking agent")
        }
        ) {
          return Future.successful{
            Responses.InvalidRequest(Some(s"Only Object with type ${ParkingFacility.mvType(Some("mv:"))} are allowed as childs of $parkingFacilitiesPath.")) 
          }
        }
        facilities.collect{
          case obj: Object => obj
          }.foreach{
            obj: Object => 
              val path = obj.path / "ParkingSpaces"
              val psFormatCheck = wodf.childsWithType(path,ParkingSpace.mvType(Some("mv:"))).forall{
                case node: Object =>  true
                case node: Objects =>  false
                case node: InfoItem =>  false
              }
              if( !psFormatCheck){
                return Future.successful{
                  Responses.InvalidRequest(Some(s"Only Objects with type ${ParkingSpace.mvType(Some("mv:"))} are allowed as childs of ${obj.path / "ParkingSpaces"}.")) 
                }
              }
          }
    }
    writeToDB(write.replaceOdf(wodf)).flatMap{
      response: ResponseRequest =>
        val success = response.results.filter{
          result => result.returnValue.returnCode.startsWith("2")
        }
        if( success.nonEmpty ){
          log.info( s"Write successful")
          updateIndexesAndCapacities( wodf.toImmutable,response)
        } else  Future.successful(response)
    }
  }
  def calculateCapacities(updatedPFs: Set[Path]) : Future[ResponseRequest]={
    if( !calculateCapacitiesEnabled) return  Future.successful(Responses.Success())
    
    val read = ReadRequest( ImmutableODF(updatedPFs.map{ path => Object(path)}.toSeq), ttl = 1.minutes)
    readFromDB(read).flatMap{
      response: ResponseRequest =>
        val (success: Seq[OmiResult], failures: Seq[OmiResult]) = response.results.partition(_.returnValue.returnCode == "200")
        val capacityODF = success.flatMap{
          result: OmiResult => 
            result.odf.map{
              odf: ODF =>
                updatePrefixes(odf)
                setPrefixes(odf).toImmutable
            }.map{
              odf: ImmutableODF =>
                val facilities = odf.nodesWithType(ParkingFacility.mvType(Some("mv:")))
                log.info(s"Found ${facilities.size} facilities to calculate capacities.")
                facilities.collect{
                  case obj: Object =>

                    ParkingFacility.parseOdf(obj.path, odf.toImmutable, prefixes.toMap) match{
                      case Success( ps: ParkingFacility ) =>
                        (obj.path,ps)
                      case Failure( NonFatal(e) ) =>
                        throw e
                    }
                }.map{
                  case (path: Path, pf: ParkingFacility ) =>
                    //log.debug(s"Updating capacities of $path")
                    val newPF = new ParkingFacility(
                      pf.name,
                      capacities = pf.capacities.map{
                        capacity: ParkingCapacity =>
                          val spaces = pf.parkingSpaces.filter{
                            ps: ParkingSpace =>
                              (
                                (  capacity.validUserGroup.nonEmpty && capacity.validUserGroup.forall{
                                ug: UserGroup => ps.validUserGroups.contains( ug )
                                } ) || 
                                (capacity.validUserGroup.isEmpty &&  ps.validUserGroups.isEmpty ) 
                              ) && capacity.validForVehicle.forall{
                                vt: VehicleType => ps.validForVehicle.contains( vt )
                              } && capacity.maximumParkingHours.forall{
                                mph => ps.maximumParkingHours.forall( _ >= mph)
                              }
                          }

                          val current = spaces.count{
                              ps: ParkingSpace =>
                                ps.available.nonEmpty && // isOccupied fixed in ParkingSpace parsing
                                ps.available.forall{ b: Boolean => b }
                            }.toLong
                          //log.debug( s"Updating ${capacity.name}: current ${capacity.current} to $current, max ${capacity.maximum} to ${spaces.length}")
                          capacity.copy(
                            current = Some(current),
                            maximum = Some(spaces.length)
                          )
                        
                      }
                    )
                    ImmutableODF(newPF.toOdf(path.getParent,tmprefixes))
              }.fold(ImmutableODF()){
                case (l: ImmutableODF,r:ImmutableODF) =>
                  l.union(r).toImmutable
              }.toImmutable
            }
          }.fold(ImmutableODF()){
            case (l: ImmutableODF,r:ImmutableODF) =>
              l.union(r).toImmutable
          }.toImmutable
      writeToDB(WriteRequest(capacityODF, ttl = 1.minutes))
    }
  }

  val parameterPath =  Path("Objects","Parameters")
  val destinationParamPath: Path = parameterPath / "Destination"
  val vehicleParamPath: Path = parameterPath / "Vehicle"
  val arrivalTimeParamPath: Path = parameterPath / "ArrivalTime"
  val distanceFromDestinationParamPath: Path = parameterPath / "DistanceFromDestination"
  val validForUserGroupParamPath: Path = parameterPath / "ParkingUserGroup"
  val chargerParamPath: Path = parameterPath / "Charger"
  var lastTime: Timestamp = currentTimestamp
  override def handleCall(call: CallRequest) : Future[ResponseRequest] = {
    //val timer = LapTimer(log.info)
    updatePrefixes(call.odf)
    //timer.step("update prefixes")
    val codf = setPrefixes(call.odf) 
    //timer.step("set prefixes")
    val r:Option[Future[ResponseRequest]] = codf.get( findParkingPath ).map{
      case ii: InfoItem =>
        ii.values.collectFirst {
          case value: ODFValue =>
            updatePrefixes(value.value.toImmutable)
            val odf: ImmutableODF = setPrefixes(value.value.toImmutable).toImmutable
            val param = odf.get(parameterPath)
            param.collect {
              case ii: InfoItem =>
                Future.successful {
                  Responses
                    .InvalidRequest(Some(s"Found ${
                      ii
                        .path
                    } InfoItem from input when Object is expected. Refer to O-DF guidelines stored in MetaData."))
                }
              case obj: Object =>
                odf.get(destinationParamPath).collect {
                  case ii: InfoItem =>
                    return Future.successful {
                      Responses
                        .InvalidRequest(Some(s"Found ${
                          ii
                            .path
                        } InfoItem from input when Object is expected. Refer to O-DF guidelines stored in MetaData."))
                    }
                  case obj: Object =>
                    GeoCoordinates.parseOdf(obj.path, odf, prefixes.toMap) match {
                      case Failure(e: ParseError) =>
                        Future.successful {
                          Responses.ParseErrors(Vector(e))
                        }
                      case Failure(NonFatal(e)) =>
                        Future.successful{
                          Responses.InternalError(e)
                        }
                      case Success(destination: GeoCoordinates) =>
                        val distance: Double = getDoubleOption("DistanceFromDestination", parameterPath, odf)
                          .getOrElse(1000.0)
                        val wantCharging: Option[Boolean] = getBooleanOption("wantCharging", parameterPath, odf)
                        val userGroup = getStringOption("UserGroup", parameterPath, odf).map {
                          str =>
                            str.split(",").map {
                              subStr =>
                                val ug = UserGroup(subStr,prefixes.toMap)
                                ug
                            }
                        }.toSeq.flatten.flatten
                        val vehicle = odf.get(vehicleParamPath).collect {
                          case ii: InfoItem =>
                            return Future.successful {
                              Responses
                                .InvalidRequest(Some(s"Found ${
                                  ii
                                    .path
                                } InfoItem from input when Object is expected. Refer to O-DF guidelines stored in " +
                                                       s"MetaData."))
                            }
                          case obj: Object =>
                            Vehicle.parseOdf(obj.path, odf, prefixes.toMap) match {
                              case Success(v: Vehicle) => v
                              case Failure(e) =>
                                return Future.successful {
                                  Responses.InvalidRequest(Some(s"Incorrect vehicle parameter format. ${e.getMessage}"))
                                }

                            }
                        }
                        val charger = odf.get(chargerParamPath).collect {
                          case ii: InfoItem =>
                            return Future.successful {
                              Responses
                                .InvalidRequest(Some(s"Found ${
                                  ii
                                    .path
                                } InfoItem from input when Object is expected. Refer to O-DF guidelines stored in " +
                                                       s"MetaData."))
                            }
                          case obj: Object =>
                            Charger.parseOdf(obj.path, odf, prefixes.toMap) match {
                              case Success(v: Charger) => v
                              case Failure(e) =>
                                return Future.successful {
                                  Responses.InvalidRequest(Some(s"Incorrect Charger parameter format. ${e.getMessage}"))
                                }
                            }

                        }
                        log.debug("FindParking parameters parsed")
                        //timer.step("FindParking parameters parsed")
                        findParking(destination, distance, vehicle, userGroup, charger, wantCharging)
                    }
                }.getOrElse {
                  Future.successful {
                    Responses
                      .InvalidRequest(Some(s"Could not find Destination Object from input. Refer to O-DF guidelines stored in MetaData."))
                  }
                }
              /*
              val charging = getBooleanOption( "wantCharging", obj.path, odf)
              */

            }.getOrElse {
              Future.successful {
                Responses
                  .InvalidRequest(Some(s"Could find Objects/Parameters Object from given O-DF input. Refer to O-DF guidelines stored in MetaData."))
              }
            }
        }.getOrElse{
          Future.successful{
            Responses.InvalidRequest(Some(s"$findParkingPath path should contain value with type odf."))
          }
        }
      case n: Node =>
        Future.successful{
          Responses.InvalidRequest(Some(s"$findParkingPath path should be InfoItem."))
        }
    }
    val t = r.getOrElse{
      Future.successful{
        Responses.InvalidRequest(Some(s"Call request doesn't contain $findParkingPath path."))
      }
    }
    //t.foreach{
      //_ => 
        //timer.step("find parking")
        //timer.total()
    //}
    t
  }

  def findParking(
    destination: GeoCoordinates, 
    maxDistance: Double, 
    vehicle: Option[Vehicle], 
    userGroups: Seq[UserGroup], 
    charger: Option[Charger], 
    wantCharging: Option[Boolean]
  ):Future[ResponseRequest] ={
      val timer = LapTimer(log.info)
      val radius: Double = 6371e3
      val deltaR = maxDistance / radius 
      val latP: Set[Path]  = latitudePFIndex.keySet.dropWhile{
        latitude: Double  => 
          latitude.toRadians < destination.latitude.toRadians - deltaR
      }.toSeq.reverse.dropWhile{
        latitude: Double  => 
          latitude.toRadians > destination.latitude.toRadians + deltaR
      }.flatMap{
        latitude: Double  => 
          latitudePFIndex.get(latitude) 
      }.flatten.toSet
      val longP: Set[Path]  = longitudePFIndex.keySet.dropWhile{
        longitude: Double  => 
          longitude.toRadians < destination.longitude.toRadians - deltaR
      }.toSeq.reverse.dropWhile{
        longitude: Double  => 
          longitude.toRadians > destination.longitude.toRadians + deltaR
      }.flatMap{
        longitude: Double  => 
          longitudePFIndex.get(longitude) 
      }.flatten.toSet
      val pfPaths: Set[Path] = latP.intersect(longP)
      timer.step("position filtered")
      log.info( s"Current indexes has: ${latitudePFIndex.values.map(_.size).sum} parking facilities")
      log.info(s"Found ${pfPaths.size} parking facilities close to destination")
      if( pfPaths.isEmpty ){
        Future.successful{ Responses.Success(objects= Some(ImmutableODF( Seq( Object(parkingFacilitiesPath))))) }
      } else {
        val request = ReadRequest(ImmutableODF(pfPaths.flatMap{
          facilityPath: Path =>
            val pf = new ParkingFacility( facilityPath.last )
            pf.toOdf(facilityPath.getParent,Map("http://www.schema.mobivoc.org/" -> "mv:"))
        }), ttl = 1.minutes)
        timer.step("request created")
        readFromDB(request).map{
          response: ResponseRequest =>
            timer.step("response received")
            val (succs, fails) = response.results.partition{
              result => result.returnValue.returnCode == "200"
            }
            timer.step("result partition")
            val newOdf = succs.flatMap{
              result =>
                result.odf/*.map{
                  odf: ODF => 
                    timer.step("pre filtering responses")
                    updatePrefixes(odf)
                    val t = setPrefixes(odf).immutable
                    timer.step("result prefixes")
                    t
                }*/.map{
                  odf: ODF => 
                    log.debug( "Found Successful result with ODF")
                    val correctParkingSpaceAndCapacityPaths  = odf.nodesWithType(ParkingFacility.mvType(Some("mv:"))).collect{
                      case obj: Object =>
                        ParkingFacility.parseOdf(obj.path, odf.toImmutable, prefixes.toMap) match {
                          case Success( ps: ParkingFacility ) =>
                            obj.path -> ps
                          case Failure( e ) =>
                            throw e
                        }
                    }.flatMap{
                      case (path: Path, pf: ParkingFacility) =>
                        val correctParkingSpaces = pf.parkingSpaces.filter{ 
                          parkingSpace => 
                            parkingSpaceFilter(parkingSpace, vehicle, userGroups, charger, wantCharging)
                        }

                        log.debug(s"Found ${correctParkingSpaces.size} valid parking spaces from $path")
                        val correctCapacities = pf.capacities.filter{
                          capacity => capacityFilter( capacity, vehicle, userGroups)
                        }
                        log.debug(s"Found ${correctCapacities.size} valid capacites from $path")
                        
                        if( correctParkingSpaces.nonEmpty || correctCapacities.nonEmpty) { 
                          correctParkingSpaces.map(
                            path / "ParkingSpaces" / _.id
                          ) ++ correctCapacities.map(
                            path / "Capacities" / _.name
                          )
                        } else { 
                          log.debug(s"No matching parking spaces or capacities for $path")
                          Vector.empty 
                        }
                    }
                    timer.step("correct PS Cap filtered")
                    log.info(s"Found total ${correctParkingSpaceAndCapacityPaths.size} of valid parking spaces and capacities")
                    val unwantedCapacitiesAndSpaces = {
                      odf.nodesWithType(ParkingSpace.mvType(Some("mv:"))) ++ 
                      odf.nodesWithType(s"mv:Capacity") ++ 
                      odf.nodesWithType(s"mv:RealTimeCapacity")
                    }.map(_.path).filterNot{
                      path => correctParkingSpaceAndCapacityPaths.contains(path)
                    } ++ {
                      if( wantCharging.getOrElse(false) ){
                        pfPaths.filterNot{
                          path =>
                            correctParkingSpaceAndCapacityPaths.contains(path)
                            correctParkingSpaceAndCapacityPaths.exists{
                              foundPath => path.isAncestorOf(foundPath)
                            }
                        }
                      } else Vector.empty
                    }

                    timer.step("unwanted Cap collected")
                    val correctNodes = odf -- ( unwantedCapacitiesAndSpaces.toSeq)

                    correctNodes
                }
            }.fold(ImmutableODF()){(l: ODF, r: ODF) => l.union(r).toImmutable }
            timer.step("response filtered")
            timer.total()
            Responses.Success(objects = Some(newOdf))
        }
      }
  }
  def parkingSpaceFilter(
    ps: ParkingSpace, 
    vehicle: Option[Vehicle], 
    userGroups: Seq[UserGroup], 
    charger: Option[Charger], 
    wantCharging: Option[Boolean]
  ): Boolean ={
    log.debug( "Checking " + ps.id )
    if( ps.available.isEmpty && ps.chargers.nonEmpty) log.debug(s"ParkingSpace without available, but has charger")
    if( ps.available.forall(b => b) ) {
      lazy val validVehicle = ps.validForVehicle.isEmpty || ps.validForVehicle.exists{ 
        vt: VehicleType =>
          vehicle.forall{
            v =>
              val typeCheck = {
                v.vehicleType == vt ||
                v.vehicleType == VehicleType.Vehicle ||
                (v.vehicleType == VehicleType.ElectricVehicle && vt == VehicleType.Car && wantCharging.forall( !_ ))
              }
              val dimensionCheck ={
                v.length.forall{ vl =>  ps.length.forall{ pl => vl <= pl }} && 
                v.width.forall{ vl =>  ps.width.forall{ pl => vl <= pl }} && 
                v.height.forall{ vl =>  ps.height.forall{ pl => vl <= pl }}  
              }
              log.debug(s"vehicle $typeCheck $dimensionCheck")
              typeCheck && dimensionCheck
          }
      } 
      lazy val correctUserGroup = (ps.validUserGroups.isEmpty || ps.validUserGroups.exists{
        pug => userGroups.contains(pug)
      }) 
      lazy val validCharger = {
        (charger.isEmpty && wantCharging.forall( !_) ) || 
        (
          ( wantCharging.exists(b => b) || charger.nonEmpty ) && 
          ps.chargers.exists{
            pchar =>
              charger.forall{
                char =>
                  val correctChar =
                    char.brand.forall( pchar.brand.contains(_))&&
                  char.model.forall( pchar.model.contains(_))&&
                  char.currentType.forall( pchar.currentType.contains(_))&&
                  char.threePhasedCurrentAvailable.forall( pchar.threePhasedCurrentAvailable.contains(_))&&
                  char.isFastChargeCapable.forall(pchar.isFastChargeCapable.contains(_)) 

                  val correctPlug = (char.plugs.isEmpty || char.plugs.exists{
                    plug =>
                      pchar.plugs.exists{
                        pplug => 
                          plug.plugType.forall( pplug.plugType.contains(_)) &&
                          plug.currentType.forall( pplug.currentType.contains(_))&&
                          plug.threePhasedCurrentAvailable.forall( pplug.threePhasedCurrentAvailable.contains(_))&&
                          plug.isFastChargeCapable.forall(pplug.isFastChargeCapable.contains(_))

                      }
                  })
                log.debug( s"Charger $correctChar Plug $correctPlug")
                correctChar && correctPlug
              }

          }
          )
      }
      log.debug(s"PS checks $validVehicle && $correctUserGroup && $validCharger" )
      validVehicle && correctUserGroup && validCharger
    } else false
  }
  def capacityFilter(
    capacity: ParkingCapacity, 
    vehicle: Option[Vehicle], 
    userGroups: Seq[UserGroup] 
  ) ={
    lazy val vehicleCheck = vehicle.forall{
      v => 
        log.debug( s" VT ${v.vehicleType} and ${capacity.validForVehicle.mkString("\n")}" )
        val a = v.vehicleType == VehicleType.Vehicle  
        val b = capacity.validForVehicle.contains(v.vehicleType) 
        val c = (v.vehicleType == VehicleType.ElectricVehicle && capacity.validForVehicle.contains( VehicleType.Car))
        log.debug( s"$a || $b || $c" )
        a || b || c
    } 
    lazy val userGroupCheck =( capacity.validUserGroup.isEmpty || capacity.validUserGroup.exists{
          pug => userGroups.contains(pug)
        })
    log.debug(s"Capacity checks $vehicleCheck && $userGroupCheck" )
    vehicleCheck && userGroupCheck
  }

  def updatePrefixes(odf: ODF ): Unit = {
    odf.nodesWithAttributes.view.filter{
      node => node.attributes.contains("prefix")
    }.flatMap{
      node => node.attributes.get("prefix")
    }.foreach{
      prefixesStr: String =>
        val splited = prefixesStr.split(" ").toVector
        val pairs = splited.grouped(2).toVector
        val formatCheck = pairs.forall{
          group: Vector[String] =>
            group.size == 2 
        }
        if( formatCheck ) {
          pairs.foreach{ 
            group => 
              val value = if(  group.head.endsWith(":") ) group.head else group.head + ":" 
              prefixes.get(group.last) match {
                case Some(current: Set[String]) =>
                    prefixes.update(group.last, current ++ Set(value))
                case None =>
                  prefixes += group.last -> Set(value)
              }
          }
        } else {
          throw new Exception( s"Malformet prefixes. Each prefix should end with : and have space before value. $prefixesStr")
        }

    }
  }
  def setPrefixes(odf: ODF): ODF = { 
    val prefixToVoc: Map[String,String] = prefixes.map{
      case (voc: String, prefixes: Set[String]) =>
        prefixes.map{ prefix: String => prefix -> voc }
    }.flatten.toMap
    def fixAttributes(attributes: Map[String,String]): Map[String,String] ={
      attributes.map{
          case ( "prefix", value: String) =>
            val splited = value.split(" ").toVector
            val pairs = splited.grouped(2).toVector
            val formatCheck = pairs.forall{
              group: Vector[String] =>
                group.size == 2 &&
                group.head.endsWith(":")
            }
            if( formatCheck ) {
              val newPairs = pairs.filterNot{ 
                group => 
                  group.last == "http://www.schema.org/" ||
                  group.last == "http://www.schema.mobivoc.org/"
              }.map{
                group => 
                  val value = if(  group.head.endsWith(":") ) group.head else group.head + ":" 
                  Vector( value, group.last)
              } ++ Vector( 
                Vector("schema:", "http://www.schema.org/"),
                Vector("mv:", "http://www.schema.mobivoc.org/")
              )
              val newPrefix = newPairs.map( group => group.mkString(" ")).mkString(" ")
              "prefix" -> newPrefix
            } else {
              throw new Exception( s"Malformet prefixes. Each prefix should end with : and have space before value. $value")
            }
          case (key,value) => key -> value
      }
    }
    ImmutableODF(odf.getNodes.view.map{
      case obj: Object if obj.typeAttribute.nonEmpty =>
        obj.copy( typeAttribute ={
          obj.typeAttribute.map{
            str =>
              val t = str.split(":")
              if( t.size < 2 )
                t.mkString(":")
              else
                prefixToVoc.get(t.head) match{
                  case None => t.mkString(":")
                  case Some( "http://www.schema.org") => "schema:" + t.tail.mkString(":")
                  case Some( "http://www.schema.mobivoc.org") => "mv:" + t.tail.mkString(":")
                  case Some(x) => throw new Exception(s"unknown type attirbute: $x")
                }
          }
        })
      case ii: InfoItem if ii.typeAttribute.nonEmpty =>
        ii.copy(typeAttribute ={
          ii.typeAttribute.map{
            str =>
              val t = str.split(":")
              if( t.size < 2 )
                t.mkString(":")
              else
                prefixToVoc.get(t.head) match{
                  case None => t.mkString(":")
                  case Some( "http://www.schema.org") => "schema:" + t.tail.mkString(":") 
                  case Some( "http://www.schema.mobivoc.org") => "mv:" + t.tail.mkString(":")
                  case Some(x) => throw new Exception(s"unknown type attirbute: $x")
                }
          }
        })
      case node: Node => node

    }.map{
      case ii: InfoItem if ii.attributes.contains("prefix") =>
        ii.copy( attributes = fixAttributes(ii.attributes))
      case obj: Object if obj.attributes.contains("prefix") =>
        obj.copy( attributes = fixAttributes(obj.attributes))
      case node: Node => node
    })
  }
}
