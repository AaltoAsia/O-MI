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
package database

import scala.language.postfixOps
//import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.{SortedMap, breakOut}

import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._

trait OdfConversions extends OmiNodeTables {
  type DBValueTuple = (DBNode, Option[DBValue])
  type DBInfoItem = (DBNode, Seq[DBValue])
  type DBInfoItems = SortedMap[DBNode, Seq[DBValue]]

  def toDBInfoItems(input: Seq[DBValueTuple]): DBInfoItems = {
    SortedMap {
      input.groupBy(_._1).map {
        case (node, valuetuple) =>

          val dbValues = valuetuple.collect {
            case (_, Some(dbValue)) => dbValue
          }.reverse //maybe foldLeft so reverse is not needed?

          (node, dbValues)
      }(breakOut): _*
    }(DBNodeOrdering)
  }
  def toDBInfoItem(tupleData: Seq[DBValueTuple]): Option[DBInfoItem] = {
    val items = toDBInfoItems(tupleData)
    assert(items.size <= 1, "Asked one infoitem, should contain max one infoitem")
    items.headOption
  }

}
