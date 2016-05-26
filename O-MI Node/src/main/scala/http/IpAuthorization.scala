/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package http

import scala.collection.JavaConverters._
import java.net.InetAddress
import spray.routing.Directives.clientIP
import spray.routing.Directive1

import types.OmiTypes._

import Boot.settings
import Authorization.AuthorizationExtension

// TODO: maybe move to Authorization package

/** Trait for checking, is connected client IP permitted to do input actions, an ExternalAgent or using Write request.
  * Tests against whitelisted ips and ip masks in configuration.
  */
trait IpAuthorization extends AuthorizationExtension {
  private type UserData = Option[InetAddress]

  /** Contains white listed IPs
    *
    **/
  private[this] val whiteIPs = settings.inputWhiteListIps.asScala.map{
    case s: String => 
    val ip = inetAddrToBytes(InetAddress.getByName(s)) 
    log.debug("IPv" + ip.length + ": " + ip.mkString("."))  // TODO: bytes should be printed as unsigned
    ip
  }.toVector

  log.debug(s"Totally ${whiteIPs.length} IPs")

  /** Contains masks of white listed subnets.
    *
    **/
  private[this] val whiteMasks = settings.inputWhiteListSubnets.asScala.map{ 
    case (str: String) => 
    val parts = str.split("/")
    require(parts.length == 2)
    val mask = parts.head
    val bits = parts.last
    val ip = inetAddrToBytes(InetAddress.getByName(mask)) 
    log.debug("Mask IPv" + ip.length + " : " + ip.mkString(".")) // TODO: bytes should be printed as unsigned
    (ip, bits.toInt )
  }.toMap 
  log.debug(s"Totally ${whiteMasks.keys.size} masks")


  // FIXME: NOTE: This will fail if there isn't setting "remote-address-header = on"
  private def extractIp: Directive1[Option[InetAddress]] = clientIP map (_.toOption)

  def ipHasPermission: UserData => OmiRequest => Option[OmiRequest] = user => {
    // Write and Response are currently PermissiveRequests
    case r : PermissiveRequest =>
      val result = if (user.exists( addr =>
        whiteIPs.contains( inetAddrToBytes( addr ) ) ||
        whiteMasks.exists{
          case (subnet : Seq[Byte], bits : Int) =>
          isInSubnet(subnet, bits, inetAddrToBytes( addr ))
        }
      )) Some(r)
      else None

      if (result.nonEmpty) {
        log.debug(s"Authorized IP: $user")
      }
      
      result
    // Read and Subscriptions should be allowed elsewhere
    case _ => None
   }

  abstract override def makePermissionTestFunction =
    combineWithPrevious(
      super.makePermissionTestFunction,
      extractIp map ipHasPermission)
  
  /** Helper method for converting InetAddress to sequence of Bytes.
    *
    * @param addr addr is InetAddress of connector.
    * @return sequence of bytes.
    **/
  private[this] def inetAddrToBytes(addr: InetAddress) : Seq[Byte] = {
    addr.getAddress().toList
  }
  
  /** Helper method for checkking if connection is in allowed subnets.
    *
    * @param addr addr is InetAddress of connector.
    * @return Boolean, true if connection is in allowed suybnet.
    **/
  private[this] def isInSubnet(subnet: Seq[Byte], bits: Int, ip: Seq[Byte]) : Boolean = {
    // TODO: bytes should be printed as unsigned
    def compareLog() = log.debug("Whitelist check for IPv" + ip.length +
      " address: " + ip.map{b => b.toHexString}.mkString(":") +
    " against " + subnet.map{b => b.toHexString}.mkString(":")
    )
    val ipv4 = 4
    val ipv6 = 16
    (subnet.length, ip.length) match { 
      case (a, b) if a == ipv4 && b == a =>{
        compareLog()
        val maxBits = 32
        val allOnes = -1
        val mask = allOnes << (maxBits - bits)  

        val check = (bytesToInt(subnet) & mask) == (bytesToInt(ip) & mask)

        check
      }
      case (a, b) if a == ipv6 && b == a =>{
        compareLog()
          val maxBits = 64
          val allOnes = -1
          val mask = allOnes << (maxBits - bits)  
          val ipArea = bytesToInt( List( ip(4), ip(5), ip(6), ip(7) ) )
          val subnetArea = bytesToInt( List( subnet(4), subnet(5), subnet(6), subnet(7) ) )

      /*if( bits > 56 )
       List[Byte]( 0xFF.toByte, (0xFF << ( 64 - bits)).toByte)
       else 
         List[Byte]( (0xFF << ( 56 - bits)).toByte , 0x00.toByte )
       */

      def check( subnet: Seq[Byte], ip: Seq[Byte], n : Int): Boolean = n match {
          case v if v > -1 =>
          val masker = 0xFF 
          check(subnet, ip, n - 1) && ( subnet(n)   & masker  ) == (  ip(n)   & masker  )
          case v if v < 0 =>  true
      }
      val n = 5
      val checked = check(subnet, ip,  n) && (( subnetArea  & mask  ) == (  ipArea  & mask ))
      //( subnet(6) & mask(0) ) == ( ip(6) & mask(0)  ) && 
      //( subnet(7) & mask(1) ) == ( ip(7) & mask(1)  )
        checked
      }
      case ( a, b) if (( a == ipv4 ) && ( b == ipv6 ) )|| (( a == ipv6 ) && ( b == ipv4 )) => {
      log.debug(
        s"Tried to compare IPv$a with IPv$b, address: " + 
        subnet.map{b => b.toHexString}.mkString(":") + 
        " ip: " + ip.map{b => b.toHexString}.mkString(":") )
      false
      }
      case (_,_) => false
    }
  }

  /** Helper method for converting byte array to Int
    *
    * @param bytes bytes to be converted.
    * @return Int, bytes presented as Int.
    **/
  private[this] def bytesToInt(bytes: Seq[Byte]) : Int = {
    val ip : Int = ((bytes(0) & 0xFF) << 24) |
      ((bytes(1) & 0xFF) << 16) |
      ((bytes(2) & 0xFF) << 8)  |
      ((bytes(3) & 0xFF) << 0);
    ip
  }

}
