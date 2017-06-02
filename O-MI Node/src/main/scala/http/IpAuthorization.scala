/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
+    Copyright (c) 2015 Aalto University.                                        +
+                                                                                +
+    Licensed under the 4-clause BSD (the "License");                            +
+    you may not use this file except in compliance with the License.            +
+    You may obtain a copy of the License at top most directory of project.      +
+                                                                                +
+    Unless required by applicable law or agreed to in writing, software         +
+    distributed under the License is distributed on an "AS IS" BASIS,           +
+    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
+    See the License for the specific language governing permissions and         +
+    limitations under the License.                                              +
+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package http

import java.net.InetAddress

import scala.collection.JavaConverters._
import scala.util.{Success, Failure}

import akka.http.scaladsl.model.RemoteAddress
import http.Authorization.{UnauthorizedEx, AuthorizationExtension, CombinedTest, PermissionTest}
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.extractClientIP
import types.OmiTypes._

// TODO: maybe move to Authorization package

/** Trait for checking, is connected client IP permitted to do input actions, an ExternalAgent or using Write request.
 * Tests against whitelisted ips and ip masks in configuration.
 */
trait IpAuthorization extends AuthorizationExtension {
  val settings : OmiConfigExtension
  private type UserData = RemoteAddress

  /** Contains white listed IPs
   *
   **/
  private[this] lazy val whiteIPs = settings.inputWhiteListIps
  //log.debug(s"Totally ${whiteIPs.length} IPs")

  /** Contains masks of white listed subnets.
   *
   **/
  private[this] lazy val whiteMasks = settings.inputWhiteListSubnets
  //log.debug(s"Totally ${whiteMasks.keys.size} masks")


  // FIXME: NOTE: This will fail if there isn't setting "remote-address-header = on"
  private def extractIp: Directive1[RemoteAddress] = extractClientIP

  def ipHasPermission: UserData => PermissionTest = user => (wrap: RequestWrapper) =>
    wrap.unwrapped flatMap {
      // Write and Response are currently PermissiveRequests
      case r : PermissiveRequest =>
        val result = if (user.toOption.exists( addr =>
            whiteIPs.contains( inetAddrToBytes( addr ) ) ||
            whiteMasks.exists{
              case (subnet : InetAddress, subNetMaskLenght : Int) =>
                isInSubnet(subnet, subNetMaskLenght, addr)
            }
            )) Success(r)
        else Failure(UnauthorizedEx())

        if (result.isSuccess) {
          log.debug(s"Authorized IP: $user")
        }

        result
        // Read and Subscriptions should be allowed elsewhere
              case _ => Failure(UnauthorizedEx())
    }

    abstract override def makePermissionTestFunction: CombinedTest =
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
     * @param ip addr is InetAddress of connector.
     * @return Boolean, true if connection is in allowed suybnet.
     **/
    private[this] def isInSubnet(subnet: InetAddress, subNetMaskLenght: Int, ip: InetAddress) : Boolean = {
      // TODO: bytes should be printed as unsigned
      def compareLog() = log.debug("Whitelist check for IP address: " + ip.getHostAddress +
        " against " + subnet.getHostAddress
        )
      val ipv4 = 4
      val ipv6 = 16
      val subnetBytes = inetAddrToBytes(subnet)
      val ipBytes = inetAddrToBytes(ip)
      (subnetBytes.length, ipBytes.length) match {
        case (a, b) if a == ipv4 && b == a =>{
          compareLog()
          val maxBits: Int = 32
          val allOnes: Int = -1
          val shiftBy: Int = (maxBits - subNetMaskLenght) 
          //Without  if-clause, shifting by 32 or more is undefined behaviour
          //OracleJDK: Shifts correctly, OpenJDK: DOES NOT SHIFT AT ALL
          val mask: Int = if( shiftBy >= 32 ) 0 else allOnes << shiftBy  

          val subnetInt: Int = bytesToInt(subnetBytes) 
          val ipInt: Int = bytesToInt(ipBytes)
          val check = (subnetInt & mask) == (ipInt & mask)

          check
        }
        case (a, b) if a == ipv6 && b == a =>{
          compareLog()
          val (firstMask: Long, secondMask: Long) ={
            val allOnes: Long = -1
            val maxBits: Int = 64
            if( subNetMaskLenght <= 64 ){
              //Subnet is shorter than or exactly 64 bits. Only first Long is needs
              //mask.
              val shiftBy: Int = (maxBits - subNetMaskLenght) 
              //Without  if-clause, shifting by 64 or more is undefined behaviour
              //OracleJDK: Shifts correctly, OpenJDK: DOES NOT SHIFT AT ALL
              (if(shiftBy >= 64 ){0} else {allOnes << shiftBy}, 0l)
            } else if ( subNetMaskLenght > 64){
              //Subnet is longer than 64 bits. Only second Long is needs
              //shift. First one is taken as whole.
              val shiftBy: Int = (subNetMaskLenght - maxBits) 
              (allOnes, allOnes << shiftBy)
            } 
          }
          //Helper for getting address as two longs.
          def getAsTwoLongs( bytes: Seq[Byte] ) : (Long, Long) ={
            val (first8Bytes, last8Bytes) = bytes.splitAt(8)
            ( bytesToLong(first8Bytes), bytesToLong(last8Bytes))
          }
          val (firstLongIP:Long , secondLongIP:Long  ) = getAsTwoLongs( ipBytes ) 
          val (firstLongSubnet:Long , secondLongSubnet:Long  ) = getAsTwoLongs( subnetBytes ) 
          //Check first Long, first 64 bits, with mask and match
          lazy val firstCheck: Boolean = (firstLongSubnet & firstMask ) == (firstLongIP & firstMask)
          //Check second Long, last 64 bits, with mask and match
          lazy val secondCheck: Boolean = (secondLongSubnet & secondMask ) == (secondLongIP & secondMask)
          val check = firstCheck && secondCheck

          check
        }
           case ( a, b) if (( a == ipv4 ) && ( b == ipv6 ) )|| (( a == ipv6 ) && ( b == ipv4 )) =>
             false
           case (_,_) => false
      }
    }

    /** Helper method for converting byte array to Int
     *
     * @param bytes bytes to be converted.
     * @return Int, bytes presented as Int.
     **/
    private[this] def bytesToInt(bytes: Seq[Byte]) : Int = {
      /*
      val ip : Int = ((bytes(0) & 0xFF) << 24) |
      ((bytes(1) & 0xFF) << 16) |
      ((bytes(2) & 0xFF) << 8)  |
      ((bytes(3) & 0xFF) << 0)
      ip*/
      val ip : Int = (0 until 4).map{
        case byteIndex: Int =>
          val converted: Int = (bytes(byteIndex) & 0xFF)
          val shiftBy: Int = 32 - 8 * ( byteIndex + 1 )
          val shifted: Int = converted << shiftBy
          shifted
      }.foldLeft(0){
        case (ip: Int, byteInt: Int) =>
          byteInt | ip
      }
      ip
    }

    /** Helper method for converting byte array to Long
     *
     * @param bytes bytes to be converted.
     * @return Long, bytes presented as Long.
     **/
    private[this] def bytesToLong(bytes: Seq[Byte]) : Long = {
      val ip : Long = (0 until 8).map{
        case byteIndex: Int =>
          val converted: Long = (bytes(byteIndex) & 0xFF)
          val shiftBy: Int = 64 - 8 * ( byteIndex + 1 )
          val shifted: Long = converted << shiftBy
          shifted
      }.foldLeft(0l){
        case (ip: Long, byteLong: Long) =>
          byteLong | ip
      }
      ip
    }

}
