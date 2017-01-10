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

package analytics

import java.util.{TimeZone, Calendar}

import scala.concurrent.Await
import scala.concurrent.duration._

import agentSystem.AgentSystem
import akka.actor.{Props, ActorSystem}
import akka.stream.ActorMaterializer
import akka.testkit.{TestKit, TestActorRef, TestProbe}
import com.typesafe.config.ConfigFactory
import database.{TestDB, OdfTree, SingleStores}
import http.OmiConfigExtension
import org.prevayler.Transaction
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.AfterAll
import responses.{RequestHandler, SubscriptionManager, CallbackHandler}
import types.OdfTypes._

class AnalyticsStoreTest extends Specification with Mockito with AfterAll {
  implicit val system = ActorSystem("AnalyticsStoreTest", ConfigFactory.parseString(
    """
            akka.loggers = ["akka.testkit.TestEventListener"]
            akka.stdout-loglevel = INFO
            akka.loglevel = WARNING
            akka.log-dead-letters-during-shutdown = off
            akka.jvm-exit-on-fatal-error = off
    """))
  def afterAll = Await.ready(system.terminate(), 2 seconds)

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  val conf = ConfigFactory.load("testconfig")
  implicit val settings = new OmiConfigExtension(
    conf
  )
  implicit val callbackHandler: CallbackHandler = new CallbackHandler(settings)( system, materializer)

  implicit val singleStores = new SingleStores(settings)
  implicit val dbConnection: TestDB = new TestDB("subscription-test-db")(
    system,
    singleStores,
    settings
  )

  val subscriptionManager = system.actorOf(SubscriptionManager.props(), "subscription-handler")
  val agentSystem = system.actorOf(
    AgentSystem.props(None),
    "agent-system-test"
  )
  val analyticsStore = system.actorOf(AnalyticsStore.props(singleStores,true,true,true, 10 minutes, 10 minutes, 10 minutes, 5, 5,5 seconds))

  val requestHandler : RequestHandler = new RequestHandler(
  )(system,
    agentSystem,
    subscriptionManager,
    settings,
    dbConnection,
    singleStores,
    Some(analyticsStore)
  )

  val calendar = Calendar.getInstance()
  // try to fix bug with travis
  val timeZone = TimeZone.getTimeZone("Etc/GMT+2")
  calendar.setTimeZone(timeZone)
  val date = calendar.getTime
  val testtime = new java.sql.Timestamp(date.getTime)

  1 === 1
}
