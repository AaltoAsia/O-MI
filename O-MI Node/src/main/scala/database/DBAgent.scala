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

import java.sql.Timestamp

import scala.concurrent.Future

import akka.actor.Actor
import types.OdfTypes.{OdfValue, OdfObjects, OdfNode}
import types.Path

/**
 * Created by satsuma on 17.6.2016.
 */

case class NewDataEvent(path: Path, newData: OdfValue)
case class WriteMany(data: Seq[(Path,OdfValue)])
case class GetNBetween(requests: Iterable[OdfNode],
                       begin: Option[Timestamp],
                       end: Option[Timestamp],
                       newest: Option[Int],
                       oldest: Option[Int]
                        )

trait DBAgentBehavior {
 this: Actor =>

  val parent = context.parent

   /**
   * Used to get result values with given constrains in parallel if possible.
   * first the two optional timestamps, if both are given
   * search is targeted between these two times. If only start is given,all values from start time onwards are
   * targeted. Similiarly if only end is given, values before end time are targeted.
   *    Then the two Int values. Only one of these can be present. fromStart is used to select fromStart number
   * of values from the begining of the targeted area. Similiarly from ends selects fromEnd number of values from
   * the end.
   * All parameters except the first are optional, given only the first returns all requested data
   *
   * @param requests SINGLE requests in a list (leafs in request O-DF); InfoItems, Objects and MetaDatas
   * @param begin optional start Timestamp
   * @param end optional end Timestamp
   * @param newest number of values to be returned from start
   * @param  oldest number of values to be returned from end
   * @return Combined results in a O-DF tree
   */
  def getNBetween(
    requests: Iterable[OdfNode],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]): Future[Option[OdfObjects]]

    /**
   * Used to set many values efficiently to the database.
   * @param data list item to be added consisting of Path and OdfValue tuples.
   */
  def writeMany(data: Seq[(Path, OdfValue)]): Future[Seq[Path]]

  val DBAgentBehavior: Receive = {
    case WriteMany(data)
      if sender() == parent => sender() ! writeMany(data)

    case GetNBetween(r, b, e, n, o)
      if sender() == parent => sender() ! getNBetween(r,b,e,n,o)

    case nd: NewDataEvent => parent ! nd //or a method maybe?
  }


}
