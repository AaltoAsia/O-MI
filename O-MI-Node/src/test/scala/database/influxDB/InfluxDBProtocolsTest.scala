package database
package influxDB

import java.util.Date
import java.sql.Timestamp
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll

import types.odf._
import types.OmiTypes.OmiReturn
import types.Path

class InfluxDBProtocolsTest( implicit ee: ExecutionEnv ) 
 extends Specification {
   import InfluxDBImplementation._
   def currentTimestamp = new Timestamp( new Date().getTime)

   "InfluxDB protocol: " >> {
      val timestamp = currentTimestamp
     "For writing: " >> {
       "correctly format different data types from InfoItems" >> {
          val path = Path("Objects","Obj","II")
          def createInfoItem( value: Value[Any] ): InfoItem = InfoItem(path, Vector( value) )
          def valStr[A](value: A) = s"""$path value=$value ${timestamp.getTime}"""
         "Double" >> {
           val value: Double = 1234.5678
           infoItemToWriteFormat( createInfoItem( DoubleValue( value, timestamp ))) shouldEqual( Vector(valStr(value)))
         }
         "Int" >> {
           val value: Int = 12345678
           infoItemToWriteFormat( createInfoItem( IntValue( value, timestamp ))) shouldEqual( Vector(valStr(value)))
         }
         "String" >> {
           val value: String = "testing"
           infoItemToWriteFormat( createInfoItem( StringValue( value, timestamp ))) shouldEqual( Vector(s"""$path value="$value" ${timestamp.getTime}""" ))
         }
         "Boolean" >> {
           val value: Boolean = true
           infoItemToWriteFormat( createInfoItem( BooleanValue( value, timestamp ))) shouldEqual( Vector(valStr(value)))
         }
       } 
     }
     /*
     "For reading:" >> {
        val n = 5
        val begin = Some(currentTimestamp)
        val end = Some(new Timestamp(currentTimestamp.getTime().toLong + 1000000))
     }*/
   }

}
