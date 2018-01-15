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

import akka.http.scaladsl.server.Directive0
import ch.megard.akka.http.cors._
 
/**
 * https://github.com/lomigmegard/akka-http-cors
 */
trait CORSSupport {
   val corsSettings: CorsSettings.Default = CorsSettings.defaultSettings.copy(
    //allowGenericHttpRequests = false,
    //allowedOrigins = HttpOriginRange.*,
    allowedHeaders = HttpHeaderRange.Default(collection.immutable.Seq(
      "Origin",
      "X-Requested-With",
      "Content-Type",
      "Accept",
      "Accept-Encoding",
      "Accept-Language",
      "Host",
      "Referer",
      "User-Agent")),
    maxAge = Some(3 * 24 * 60 * 60)
  )
  def corsEnabled: Directive0 = CorsDirectives.cors(corsSettings)
  
}

