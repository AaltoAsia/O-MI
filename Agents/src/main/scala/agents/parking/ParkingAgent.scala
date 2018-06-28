package agents.parking

import java.lang.{Iterable => JavaIterable}
import java.io.File

import scala.xml.XML
import scala.util.control.NonFatal
import scala.util.{ Success, Failure }
import scala.collection.mutable.{ SortedMap}
import scala.concurrent.Future
import scala.collection.JavaConverters._

import akka.actor.{Props, ActorRef}
import com.typesafe.config.Config

import agentSystem._ 
import types.OmiTypes._
import types.odf._
import types.Path._
import types._
import VehicleType._
import UserGroup._

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

  def configStringToPath( name: String ): Path ={
    Path(config.getString(name))
  }
  //Base path for service, contains at least all method
  val servicePath: Path = configStringToPath("servicePath")
  val parkingFacilitiesPath: Path = configStringToPath("parkingFacilitiesPath")
  val findParkingPath: Path = servicePath / "FindParking"
  val calculateCapacitiesEnabled: Boolean = if( config.hasPath("calculate-capacities") ) config.getBoolean("calculate-capacities") else false

  //Path to object containing all parking lots.
  val latitudePFIndex: SortedMap[ Double, Set[Path] ] = SortedMap.empty
  val longitudePFIndex: SortedMap[ Double, Set[Path] ] = SortedMap.empty

  //File used to populate node with initial state
  {
    val startStateFile =  new File(config.getString("initialStateFile"))
    val initialODF: ImmutableODF = if( startStateFile.exists() && startStateFile.canRead() ){
      val xml = XML.loadFile(startStateFile)
      ODFParser.parse( xml) match {
        case Left( errors : JavaIterable[ParseError]) =>
          val msg = errors.asScala.toSeq.mkString("\n")
          log.warning(s"Odf has errors, $name could not be configured.")
          log.debug(msg)
          throw AgentConfigurationException(s"File $startStateFile could not be parsed, because of following errors: $msg")
        case Right(odf: ImmutableODF) => odf
      }
    } else if( !startStateFile.exists() ){
      throw AgentConfigurationException(s"Could not get initial state for $name. File $startStateFile do not exists.")
    } else {
      throw  AgentConfigurationException(s"Could not get initial state for $name. Could not read file $startStateFile.")
    }
    def populateIndexes: Future[ResponseRequest] ={
      readFromDB(ReadRequest(odf = ImmutableODF(Seq(Object(parkingFacilitiesPath))))).map{
        response: ResponseRequest => 
          response.results.foreach{
            case result: OmiResult =>
              result.returnValue match {
                case succ: Returns.ReturnTypes.Successful =>
                  result.odf.foreach{
                    odf =>
                      updateIndexesAndCapacities(odf,response)
                  }
                  case other =>


              }
          }
          response
      }
    }

    
    val initialWrite: Future[ResponseRequest] = writeToDB( WriteRequest(initialODF) ).flatMap{
      writeResponse: ResponseRequest =>
        val wfails = writeResponse.results.filterNot{
          result => result.returnValue.returnCode.startsWith("2")
        }
        if( wfails.nonEmpty) Future{ writeResponse }
        else{
          readFromDB(ReadRequest(odf = ImmutableODF(Seq(Object(parkingFacilitiesPath))))).map{
            response: ResponseRequest => 
              val (success,fails) = response.results.partition{
                result => result.returnValue.returnCode.startsWith("2")
              }
              if( fails.nonEmpty) Future{ response }
              else{
                success.foreach{
                  case result: OmiResult =>
                    result.odf.foreach{
                      odf =>
                        updateIndexesAndCapacities(odf,response)
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
      case response: ResponseRequest => 
        response.results.foreach{
          case result: OmiResult =>
            result.returnValue match {
              case succ: Returns.ReturnTypes.Successful =>
                log.info( s"Successfully initialized state for $name" )
              case other =>
                log.warning( s"Could not set initial state for $name. Got result:$result.")
                throw new Exception( s"Could not set initial state for $name. Got following result from DB: $result.")
            }
        }
    }
  }
  def updateIndexes(pfs:Set[Object], odf: ODF): Unit ={
    pfs.filter{
      obj: Object => 
        !latitudePFIndex.values.flatten.toSet.contains(obj.path) && 
        !longitudePFIndex.values.flatten.toSet.contains(obj.path)
    }.foreach{
      node: Object =>
        val gps: Option[GeoCoordinates] = for{
          lat <- getDoubleOption("latitude", node.path / "geo",odf.immutable )
          lon <- getDoubleOption("longitude", node.path / "geo",odf.immutable )
        } yield GeoCoordinates( lat, lon)
        gps.foreach{
          gc =>
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
  }
  def updateIndexesAndCapacities(odf: ODF, response: ResponseRequest): Future[ResponseRequest]={
    val facilities = odf.nodesWithType("mv:ParkingFacility").collect{ case obj: Object => obj}
    updateIndexes(facilities,odf)
    val availableWrite = odf.nodesWithType("mv:ParkingSpace").exists{
      obj: Node =>
        odf.get(obj.path / "available").nonEmpty
    }
    if( calculateCapacitiesEnabled && availableWrite && facilities.nonEmpty ) calculateCapacities(facilities.map(_.path))
    else Future{ response }
  }
  override def handleWrite( write: WriteRequest):Future[ResponseRequest] = {
    if( write.odf.get(findParkingPath).nonEmpty ){
      return Future{
        Responses.InvalidRequest(Some(s"Writing to $findParkingPath is not allowed.")) 
      }
    }
    {
      val facilities = write.odf.getChilds( parkingFacilitiesPath)
      if( facilities.isEmpty ||
         facilities.forall{
          case node: Object => node.typeAttribute.isEmpty || !node.typeAttribute.contains("mv:ParkingFacility")
          case node: InfoItem => true
        }
      ) {
        return Future{
          Responses.InvalidRequest(Some(s"Only Object with type mv:ParkingFacility are allowed as childs of $parkingFacilitiesPath.")) 
        }
      }
      facilities.collect{
        case obj: Object => obj
      }.foreach{
        obj: Object => 
          val psFormatCheck = write.odf.getChilds(obj.path / "ParkingSpaces").forall{
            case node: Object => 
              node.typeAttribute.contains("mv:ParkingSpace")
            case node: InfoItem =>  
              false
          }
          if( !psFormatCheck){
            return Future{
              Responses.InvalidRequest(Some(s"Only Objects with type mv:ParkingSpace are allowed as childs of ${obj.path / "ParkingSpaces"}.")) 
            }
          }
      }
    }
    writeToDB(write).flatMap{
      response: ResponseRequest =>
        val success = response.results.filter{
          result => result.returnValue.returnCode.startsWith("2")
        }
        if( success.nonEmpty ){
          log.info( s"Write successful")
          updateIndexesAndCapacities( write.odf,response)
        } else Future{ response }
    }
  }
  def calculateCapacities(updatedPFs: Set[Path]) : Future[ResponseRequest]={
    if( !calculateCapacitiesEnabled) return Future{ Responses.Success()}
    
    log.debug( s"Calculating capacities")
    val read = ReadRequest( ImmutableODF(updatedPFs.map{ path => Object(path)}.toSeq))
    readFromDB(read).flatMap{
      response: ResponseRequest =>
        val (success: Seq[OmiResult], failures: Seq[OmiResult]) = response.results.partition(_.returnValue.returnCode == "200")
        if( failures.nonEmpty )
          log.debug( response.asXML.toString )
        val capacityODF = success.flatMap{
          result: OmiResult => 
            result.odf.map{
              odf: ODF =>
                val facilities = odf.nodesWithType("mv:ParkingFacility")
                log.debug(s"Found ${facilities.size} facilities to calculate capacities.")
                facilities.collect{
                  case obj: Object =>
                    ParkingFacility.parseOdf(obj.path, odf.immutable) match{
                      case Success( ps: ParkingFacility ) =>
                        (obj.path,ps)
                      case Failure( NonFatal(e) ) =>
                        throw e
                    }
                }.map{
                  case (path: Path, pf: ParkingFacility ) =>
                    log.debug(s"Updating capacities of $path")
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
                                ps.available.nonEmpty &&
                                ps.available.forall{ b: Boolean => b }
                            }.toLong
                          log.debug( s"Updating ${capacity.name}: current ${capacity.current} to $current, max ${capacity.maximum} to ${spaces.length}")
                          capacity.copy(
                            current = Some(current),
                            maximum = Some(spaces.length)
                          )
                        
                      }
                    )
                    ImmutableODF(newPF.toOdf(path.getParent))
              }.fold(ImmutableODF()){
                case (l: ImmutableODF,r:ImmutableODF) =>
                  l.union(r).immutable
              }.immutable
            }
          }.fold(ImmutableODF()){
            case (l: ImmutableODF,r:ImmutableODF) =>
              l.union(r).immutable
          }.immutable
      writeToDB(WriteRequest(capacityODF))
    }
  }

  val parameterPath =  Path("Objects","Parameters")
  val destinationParamPath: Path = parameterPath / "Destination"
  val vehicleParamPath: Path = parameterPath / "Vehicle"
  val arrivalTimeParamPath: Path = parameterPath / "ArrivalTime"
  val distanceFromDestinationParamPath: Path = parameterPath / "DistanceFromDestination"
  val validForUserGroupParamPath: Path = parameterPath / "ParkingUserGroup"
  val chargerParamPath: Path = parameterPath / "Charger"
  override def handleCall(call: CallRequest) : Future[ResponseRequest] = {
    val r:Option[Future[ResponseRequest]] = call.odf.get( findParkingPath ).map{
      case ii: InfoItem =>
        ii.values.collect{
          case value: ODFValue =>
            val odf: ImmutableODF = value.value.immutable
            val param = odf.get(parameterPath) 
            param.map{
                case ii: InfoItem =>
                  Future{
                    Responses.InvalidRequest( Some(s"Found ${ii.path} InfoItem from input when Object is expected. Refer to O-DF guidelines stored in MetaData."))
                  }
                case obj: Object  =>
                  odf.get( destinationParamPath ).map{
                    case ii: InfoItem =>
                      return Future{
                        Responses.InvalidRequest( Some(s"Found ${ii.path} InfoItem from input when Object is expected. Refer to O-DF guidelines stored in MetaData."))
                      }
                    case obj: Object => 
                      GeoCoordinates.parseOdf( obj.path, odf) match {
                        case Failure( e: ParseError ) =>
                          Future{ Responses.ParseErrors( Vector(e) ) }
                        case Failure( NonFatal(e)) => 
                          Future{ Responses.InternalError( e ) }
                        case Success( destination: GeoCoordinates) =>
                          val distance : Double = getDoubleOption( "DistanceFromDestination", parameterPath, odf).getOrElse(1000.0)
                          val userGroup = getStringOption( "UserGroup",parameterPath, odf).map{
                            str => 
                              str.split(",").map{
                                subStr => 
                                val ug = UserGroup(subStr)
                                ug
                              }
                          }.toSeq.flatten.flatten
                          val vehicle = odf.get( vehicleParamPath ).map{
                            case ii: InfoItem =>
                              return Future{
                                Responses.InvalidRequest( Some(s"Found ${ii.path} InfoItem from input when Object is expected. Refer to O-DF guidelines stored in MetaData."))
                              }
                            case obj: Object => 
                              Vehicle.parseOdf( obj.path, odf)  match{
                                case Success(v: Vehicle) => v
                                case Failure(e) =>
                                  return Future{
                                    Responses.InvalidRequest( Some(s"Incorrect vehicle parameter format. ${e.getMessage}"))
                                  }

                              }
                          }
                          val charger = odf.get( chargerParamPath).map{
                            case ii: InfoItem =>
                              return Future{
                                Responses.InvalidRequest( Some(s"Found ${ii.path} InfoItem from input when Object is expected. Refer to O-DF guidelines stored in MetaData."))
                              }
                            case obj: Object => 
                              Charger.parseOdf( obj.path, odf) match{
                                case Success(v: Charger) => v
                                case Failure(e) =>
                                  return Future{
                                    Responses.InvalidRequest( Some(s"Incorrect Charger parameter format. ${e.getMessage}"))
                                  }
                              }

                          }
                          log.info("FindParking parameters parsed")
                          findParking(destination,distance,vehicle,userGroup,charger)
                      }
                  }.getOrElse{
                      Future{
                        Responses.InvalidRequest( Some(s"Could not find Destination Object from input. Refer to O-DF guidelines stored in MetaData."))
                      }
                  }
                  /*
                  val charging = getBooleanOption( "wantCharging", obj.path, odf)
                  */

            }.getOrElse{
                Future{
                  Responses.InvalidRequest( Some(s"Could find Objects/Parameters Object from given O-DF input. Refer to O-DF guidelines stored in MetaData."))
                }
            }
        }.headOption.getOrElse{
          Future{
            Responses.InvalidRequest(Some(s"$findParkingPath path should contain value with type odf."))
          }
        }
      case n: Node =>
        Future{
          Responses.InvalidRequest(Some(s"$findParkingPath path should be InfoItem."))
        }
    }
    r.getOrElse{
      Future{
        Responses.InvalidRequest(Some(s"Call request doesn't contain $findParkingPath path."))
      }
    }
  }

  def findParking(destination: GeoCoordinates, maxDistance: Double, vehicle: Option[Vehicle], userGroup: Seq[UserGroup], charger: Option[Charger]):Future[ResponseRequest] ={
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
      if( pfPaths.isEmpty ){
        Future{ Responses.Success(objects= Some(ImmutableODF( Seq( Object(parkingFacilitiesPath))))) }
      } else {
        val request = ReadRequest(ImmutableODF(pfPaths.flatMap{
          facilityPath: Path =>
            val pf = new ParkingFacility( facilityPath.last )
            pf.toOdf(facilityPath.getParent)
        }))
        readFromDB(request).map{
          response: ResponseRequest =>
            val (succs, fails) = response.results.partition{
              result => result.returnValue.returnCode == "200"
            }
            val newOdf = succs.flatMap{
              result =>
                result.odf.map{
                  odf => 
                    log.info( "Found Successfull result with ODF")
                    val correctParkingSpaces = odf.nodesWithType("mv:ParkingSpace").collect{
                      case obj: Object =>
                        ParkingSpace.parseOdf(obj.path, odf.immutable) match {
                          case Success( ps: ParkingSpace ) =>
                            obj.path -> ps
                          case Failure( e ) =>
                            throw e
                        }
                    }.filter{
                      case (path: Path, ps: ParkingSpace) =>
                        val validVehicle = ps.validForVehicle.exists{ 
                          vt: VehicleType =>
                            vehicle.forall{
                              v =>
                                val typeCheck = {
                                  v.vehicleType == vt ||
                                  v.vehicleType == VehicleType.Vehicle ||
                                  (v.vehicleType == VehicleType.ElectricVehicle && vt == VehicleType.Car)
                                }
                                val dimensionCheck ={
                                  v.length.forall{ vl =>  ps.length.forall{ pl => vl <= pl }} && 
                                  v.width.forall{ vl =>  ps.width.forall{ pl => vl <= pl }} && 
                                  v.height.forall{ vl =>  ps.height.forall{ pl => vl <= pl }}  
                                }
                                typeCheck && dimensionCheck
                            }
                        } 
                        val correctUserGroup = (ps.validUserGroups.isEmpty || ps.validUserGroups.exists{
                          pug => userGroup.contains(pug)
                        }) 
                        val validCharger = (charger.isEmpty || {
                          ps.charger.exists{
                            pchar =>
                              charger.forall{
                                char =>
                                  char.brand.forall( pchar.brand.contains(_))&&
                                  char.model.forall( pchar.model.contains(_))&&
                                  char.currentType.forall( pchar.currentType.contains(_))&&
                                  char.threePhasedCurrentAvailable.forall( pchar.threePhasedCurrentAvailable.contains(_))&&
                                  char.isFastChargeCapable.forall(pchar.isFastChargeCapable.contains(_)) &&
                                  (char.plugs.isEmpty || char.plugs.exists{
                                    plug =>
                                      pchar.plugs.exists{
                                        pplug => 
                                          plug.plugType.forall( pplug.plugType.contains(_)) &&
                                          plug.currentType.forall( pplug.currentType.contains(_))&&
                                          plug.threePhasedCurrentAvailable.forall( pplug.threePhasedCurrentAvailable.contains(_))&&
                                          plug.isFastChargeCapable.forall(pplug.isFastChargeCapable.contains(_))

                                      }
                                  })
                              }

                          }
                        })
                        validVehicle && correctUserGroup && validCharger
                    }.flatMap{
                      case (path: Path, ps: ParkingSpace ) =>
                        ps.toOdf(path.getParent)
                    }
                    val correctCapacities= (odf.nodesWithType("mv:Capacity") ++odf.nodesWithType("mv:RealTimeCapacity")).collect{
                      case obj: Object =>
                        ParkingCapacity.parseOdf(obj.path, odf.immutable) match {
                          case Success( ps: ParkingCapacity ) =>
                            obj.path -> ps
                          case Failure( e ) =>
                            throw e
                        }
                    }.filter{
                      case (path: Path, capacity: ParkingCapacity ) =>
                        vehicle.forall{
                          v => 
                            v.vehicleType == VehicleType.Vehicle || 
                            capacity.validForVehicle.contains(v.vehicleType) ||
                            (v.vehicleType == VehicleType.ElectricVehicle && capacity.validForVehicle.contains( Car))
                        } && ( capacity.validUserGroup.isEmpty || capacity.validUserGroup.exists{
                          pug => userGroup.contains(pug)
                        })
                    }.flatMap{
                      case (path: Path, capacity: ParkingCapacity ) =>
                        capacity.toOdf(path.getParent)
                    }
                    val correctNodes = ImmutableODF(correctParkingSpaces++ correctCapacities)

                    val removedPaths= (odf.nodesWithType("mv:ParkingSpace")++
                        odf.nodesWithType("mv:Capacity")++
                        odf.nodesWithType("mv:RealTimeCapacity")
                      ).map(_.path)
                    val nOdf = odf.removePaths(removedPaths.toSeq).union(correctNodes)
                    nOdf
                }
            }.fold(ImmutableODF()){
              case (l:ImmutableODF, r: ImmutableODF) => l.union(r).immutable
            }
            Responses.Success(objects = Some(newOdf))
        }
      }
  }

}
