package http

import scala.collection.JavaConverters._
import java.net.InetAddress

/** Helper object for checking, is connected ip permited to do input actions, a ExternalAgent or using Write request.
  *
  **/
object PermissionCheck {

  import Boot.settings
  import Boot.system.log
  /** Contains white listed ips
    *
    **/
  val whiteIPs = settings.inputWhiteListIps.asScala.map{
    case s: String => 
    val ip = inetAddrToBytes(InetAddress.getByName(s)) 
    log.debug("IPv" + ip.length + " : " + ip.mkString(".")) 
    ip
  }.toArray 

  log.debug("Totally " + whiteIPs.length + "IPs")

  /** Contains masks of white listed subnets.
    *
    **/
  val whiteMasks = settings.inputWhiteListSubnets.unwrapped().asScala.map{ 
    case (s: String, bits: Object ) => 
    val ip = inetAddrToBytes(InetAddress.getByName(s)) 
    log.debug("Mask IPv" + ip.length + " : " + ip.mkString(".")) 
    (ip, bits.toString.toInt )
  }.toMap 
  log.debug("Totally " + whiteMasks.keys.size + "masks")

  /** Main method for checkking connections permission
    *
    * @param addr addr is InetAddress of connector.
    * @return Boolean, true if connection is permited to do input.
    **/
  def hasPermission(addr: InetAddress) : Boolean = {
    whiteIPs.contains( inetAddrToBytes( addr ) ) ||
    whiteMasks.exists{
      case (subnet : Array[Byte], bits : Int) =>
      isInSubnet(subnet, bits, inetAddrToBytes( addr ))
    }
  }
  
  /** Helper method for converting InetAddress to Array of Bytes.
    *
    * @param addr addr is InetAddress of connector.
    * @return Array of bytes.
    **/
  private def inetAddrToBytes(addr: InetAddress) : Array[Byte] = {
    addr.getAddress()
  }
  
  /** Helper method for checkking if connection is in allowed subnets.
    *
    * @param addr addr is InetAddress of connector.
    * @return Boolean, true if connection is in allowed suybnet.
    **/
  private def isInSubnet(subnet: Array[Byte], bits: Int, ip: Array[Byte]) : Boolean = {
    if( subnet.length == ip.length){
      log.debug("Whitelist check for IPv" + ip.length + " address: " + ip.map{b => b.toHexString}.mkString(":") + " against " + subnet.map{b => b.toHexString}.mkString(":"))
      ip.length match{
        case 4 =>{
          val mask = -1 << (32 - bits)  
          return (bytesToInt(subnet) & mask) == (bytesToInt(ip) & mask)
        }
        case 16 =>{
          val mask = -1 << (64 - bits)
          val ipArea = bytesToInt( Array( ip(4), ip(5), ip(6), ip(7) ) )
          val subnetArea = bytesToInt( Array( subnet(4), subnet(5), subnet(6), subnet(7) ) )
          /*if( bits > 56 )
            Array[Byte]( 0xFF.toByte, (0xFF << ( 64 - bits)).toByte)
          else 
            Array[Byte]( (0xFF << ( 56 - bits)).toByte , 0x00.toByte )
          */
          return ( subnet(0)   & 0xFF  ) == (  ip(0)   & 0xFF     ) && 
          ( subnet(1)   & 0xFF  ) == (  ip(1)   & 0xFF     ) &&
          ( subnet(2)   & 0xFF  ) == (  ip(2)   & 0xFF     ) && 
          ( subnet(3)   & 0xFF  ) == (  ip(3)   & 0xFF     ) &&
          ( subnet(4)   & 0xFF  ) == (  ip(4)   & 0xFF     ) && 
          ( subnet(5)   & 0xFF  ) == (  ip(5)   & 0xFF     ) &&
          ( subnetArea  & mask  ) == (  ipArea  & mask )
          //( subnet(6) & mask(0) ) == ( ip(6) & mask(0)  ) && 
          //( subnet(7) & mask(1) ) == ( ip(7) & mask(1)  )
        }
      }
    }
    log.debug("Tried to compare IPv4 with IPv6, address: " + subnet.mkString(":") + " ip: " + ip.mkString(":") )
    false
  }

  /** Helper method for converting byte array to Int
    *
    * @param bytes bytes to be converted.
    * @return Int, bytes presented as Int.
    **/
  private def bytesToInt(bytes: Array[Byte]) : Int = {
    val ip : Int = ((bytes(0) & 0xFF) << 24) |
      ((bytes(1) & 0xFF) << 16) |
      ((bytes(2) & 0xFF) << 8)  |
      ((bytes(3) & 0xFF) << 0);
    ip
  }

}
