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

package types
import scala.collection.JavaConverters._
import java.util.{GregorianCalendar, Dictionary}
import scala.concurrent.{Future, ExecutionContext}
import OmiTypes.ResponseRequest

object JavaHelpers{

 def mutableMapToImmutable[K,V]( mutable: scala.collection.mutable.Map[K,V] ) : scala.collection.immutable.Map[K,V] = mutable.toMap[K,V] 
 def requestIDsFromJava( requestIDs : java.lang.Iterable[java.lang.Long] ) : Vector[Long ]= {
    requestIDs.asScala.map(Long2long).toVector
 }
 
 def formatWriteFuture( writeFuture: Future[java.lang.Object] ) : Future[ResponseRequest] ={
   writeFuture.mapTo[ResponseRequest]
 }
  def dictionaryToMap[K,V](dict: Dictionary[K,V] ): Map[K,V] ={
    dict.asScala.toMap
  }
}
