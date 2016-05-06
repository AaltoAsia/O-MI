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
package agentSystem

import java.io.File
import java.net.URLClassLoader
import java.sql.Timestamp
import java.util.Date
import java.util.jar.JarFile

import akka.actor.SupervisorStrategy._
import akka.actor.{Actor, ActorInitializationException, ActorKilledException, ActorLogging, OneForOneStrategy, Props, SupervisorStrategy}
import http.CLICmds._
import http._

import scala.collection.JavaConverters._
import scala.collection.mutable.Map
import scala.util.{Failure, Success, Try}
