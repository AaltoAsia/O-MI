package database
package influxDB

import java.util.Date
import java.sql.Timestamp
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll

import types.odf._
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
          def createMeasurement(  value: Value[Any] ) = Measurement(
            pathToMeasurementName(path),
            value.value match {
              case odf: ImmutableODF => throw new Exception("Having O-DF inside value with InfluxDB is not supported.")
              case str: String => s""""${str.replace("\"", "\\\"")}""""
              case num @ (_: Double | _: Float | _: Int | _: Long | _:Short ) => num.toString 
              case bool: Boolean => bool.toString
              case any: Any => s""""${any.toString.replace("\"", "\\\"")}""""
            },
            value.timestamp
          ) 
          def valStr[A](value: A) = s"""$path value=$value ${timestamp.getTime}"""
         "Double" >> {
           val value = DoubleValue( 1234.5678, timestamp)
           infoItemToWriteFormat( createInfoItem( value )) shouldEqual( Vector(createMeasurement(value)))
         }
         "Int" >> {
           val value = IntValue(12345678,timestamp)
           infoItemToWriteFormat( createInfoItem( value )) shouldEqual( Vector(createMeasurement(value)))
         }
         "String" >> {
           val value = StringValue("testing",timestamp)
           infoItemToWriteFormat( createInfoItem( value )) shouldEqual( Vector(createMeasurement(value)))
         }
         "Boolean" >> {
           val value = BooleanValue(true,timestamp)
           infoItemToWriteFormat( createInfoItem( value )) shouldEqual( Vector(createMeasurement(value)))
         }
       } 
     }
     "For queries:" >> {
        val n = 5
        val begin = Some(currentTimestamp)
        val end = Some(new Timestamp(currentTimestamp.getTime().toLong + 1000000))

        val dbName = "test"
        "create correct query, clause or expression for" >>{
          "CreateDB"  >> {
            CreateDB(dbName).query must beEqualTo( s"CREATE DATABASE $dbName" ) 
          }
          "ShowDBs" >> {
            ShowDBs.query must beEqualTo( "SHOW DATABASES" )
          }
          "DropMeasurement" >> {
            DropMeasurement(dbName ).query must beEqualTo( s"""DROP MEASUREMENT "$dbName"""" )
          }
          "SelectValue without any clauses" >> {
            SelectValue( dbName, None, None, None ).query must beEqualTo(s"""SELECT value FROM "$dbName"""")
          }
          "LowerTimeBoundExpression" >> {
            LowerTimeBoundExpression(timestamp).expression must beEqualTo(s"time >= '$timestamp'")
          }
          "UpperTimeBoundExpression" >> {
            UpperTimeBoundExpression(timestamp).expression must beEqualTo(s"time <= '$timestamp'")
          }
          "DescTimeOrderByClause" >> {
            DescTimeOrderByClause().clause must beEqualTo( s"ORDER BY time DESC" )
          }
          "LimitClause" >> {
            LimitClause(n).clause must beEqualTo( s"LIMIT $n")
          }
          "WhereClause with one expression" >> {
            WhereClause( 
              Vector( 
                LowerTimeBoundExpression(timestamp)
              )
            ).clause must beEqualTo(s"WHERE time >= '$timestamp'")
          }
          "WhereClause with two expressions" >>{
            WhereClause( 
              Vector( 
                LowerTimeBoundExpression(timestamp),
                UpperTimeBoundExpression(timestamp)
              )
            ).clause must beEqualTo(s"WHERE time >= '$timestamp' AND time <= '$timestamp'")
          }
          "SelectValue with where clause" >>{
            val where = WhereClause( 
              Vector( 
                LowerTimeBoundExpression(timestamp),
                UpperTimeBoundExpression(timestamp)
              )
            )
            SelectValue( dbName, Some(where), None, None).query must beEqualTo(
              s"""SELECT value FROM "$dbName" ${where.clause}"""
            )
          }
          "SelectValue with order by clause" >>{
            val orderBy =DescTimeOrderByClause()
            SelectValue( dbName, None, Some(orderBy), None ).query must beEqualTo(
              s"""SELECT value FROM "$dbName" ${orderBy.clause}"""
            )
          }
          "SelectValue with limit clause" >>{
            val limit = LimitClause( n)
            SelectValue( dbName, None, None, Some(limit)).query must beEqualTo(
              s"""SELECT value FROM "$dbName" ${limit.clause}"""
            )
          }
          "SelectValue with where and order by clause" >>{
            val where = WhereClause( 
              Vector( 
                LowerTimeBoundExpression(timestamp),
                UpperTimeBoundExpression(timestamp)
              )
            )
            val orderBy =DescTimeOrderByClause()
            SelectValue( dbName, Some(where), Some(orderBy), None).query must beEqualTo(
              s"""SELECT value FROM "$dbName" ${where.clause} ${orderBy.clause}"""
            )
          }
          "SelectValue with where and limit clause" >>{
            val where = WhereClause( 
              Vector( 
                LowerTimeBoundExpression(timestamp),
                UpperTimeBoundExpression(timestamp)
              )
            )
            val limit = LimitClause( n)
            SelectValue( dbName, Some(where),  None, Some(limit)).query must beEqualTo(
              s"""SELECT value FROM "$dbName" ${where.clause} ${limit.clause}"""
            )
          }
          "SelectValue with order by and limit clauses" >>{
            val orderBy =DescTimeOrderByClause()
            val limit = LimitClause( n)
            SelectValue( dbName, None, Some(orderBy), Some(limit) ).query must beEqualTo(
              s"""SELECT value FROM "$dbName" ${orderBy.clause} ${limit.clause}"""
            )
          }
          "SelectValue with where, order by and limit clause" >>{
            val where = WhereClause( 
              Vector( 
                LowerTimeBoundExpression(timestamp),
                UpperTimeBoundExpression(timestamp)
              )
            )
            val orderBy =DescTimeOrderByClause()
            val limit = LimitClause( n)
            SelectValue( dbName, Some(where), Some(orderBy), Some(limit)).query must beEqualTo(
              s"""SELECT value FROM "$dbName" ${where.clause} ${orderBy.clause} ${limit.clause}"""
            )
          }
        }
     }
   }

}
