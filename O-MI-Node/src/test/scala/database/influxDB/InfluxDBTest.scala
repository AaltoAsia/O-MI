package database
package influxDB

import java.util.Date
import java.sql.Timestamp
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._

import types.odf._
import types.Path

class InfluxDBProtocolsTest() 
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
     "For reading:" >> {
        val n = 5
        val begin = currentTimestamp
        val end = new Timestamp(currentTimestamp.getTime().toLong + 1000000)
       "Filtering clause without limiters" >>{
         filteringClause(None, None, None) shouldEqual( s" ORDER BY time DESC LIMIT 1")
       }
       "Filtering clause with only begin" >>{
         filteringClause(Some( begin), None, None) shouldEqual( s"WHERE time >= '$begin' ORDER BY time DESC ")
       }
       "Filtering clause with only end" >>{
         filteringClause(None, Some( end),None) shouldEqual( s"WHERE time <= '$end' ORDER BY time DESC ")
       }
       "Filtering clause with only newest" >>{
         filteringClause(None, None, Some( n )) shouldEqual( s" ORDER BY time DESC LIMIT $n")
       }
       "Filtering clause with begin and newest" >>{
         filteringClause(Some( begin), None, Some( n )) shouldEqual( s"WHERE time >= '$begin' ORDER BY time DESC LIMIT $n")
       }
       "Filtering clause with end and newest" >>{
         filteringClause(None, Some( end), Some( n )) shouldEqual( s"WHERE time <= '$end' ORDER BY time DESC LIMIT $n")
       }
       "Filtering clause with begin, end and newest" >>{
         filteringClause(Some(begin), Some( end ), Some( n )) shouldEqual( s"WHERE time >= '$begin' AND time <= '$end' ORDER BY time DESC LIMIT $n")
       }
       "Filtering clause with begin and end" >>{
         filteringClause(Some(begin), Some( end ), None) shouldEqual( s"WHERE time >= '$begin' AND time <= '$end' ORDER BY time DESC ")
       }
       "Get N between query ">>{
         val iis = Vector( 
           InfoItem( Path("Objects","Obj","II1"),Vector.empty ), 
           InfoItem( Path("Objects","Obj","II2"),Vector.empty  )
         )
         getNBetweenInfoItemsQueryString( iis, filteringClause(None,None,None)) shouldEqual(
           """SELECT value FROM "Objects/Obj/II1"  ORDER BY time DESC LIMIT 1;
SELECT value FROM "Objects/Obj/II2"  ORDER BY time DESC LIMIT 1"""
         )
       }
     }
   }

}
