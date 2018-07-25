package database
package influxDB

import java.util.Date
import java.sql.Timestamp
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll

import types.odf._
import types.Path
class InfluxDBTest( implicit ee: ExecutionEnv ) 
 extends Specification {
   
   def inTodo = { 1 === 2}.pendingUntilFixed
   "InfluxDB " >> {
     "should create new DB if configuret one not found" >> inTodo
     "sh ould find existing DB and use it" >> inTodo
     "should write in correct format" >> inTodo
     "should read" >> {
       "in correct format" >> inTodo
       "unmarshal results correctly" >> inTodo
       "prevent oldest queries" >> inTodo
       "fail if sending throws something" >> inTodo
       "return correct O-MI Return if Server responses with failure" >> inTodo
     }
     "remove" >>{
       "should be in correct format" >> inTodo
       "should remove data from cache too" >> inTodo
       "throw exception if server response with failure" >> inTodo
     }
   }
}
