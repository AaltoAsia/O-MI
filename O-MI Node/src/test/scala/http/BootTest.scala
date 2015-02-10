//package http
//
//import org.specs2.mutable._
//
//class BootTest extends Specification {
//  "Boot" should{
//    "have port 8080 in settings" in {
//      Boot.settings.port === 8080
//    }
//    "have timeout of 5 seconds" in {
//      Boot.timeout.duration must be equalTo(scala.concurrent.duration.FiniteDuration.apply(5, "seconds"))
//    }
//    
//  }
//}