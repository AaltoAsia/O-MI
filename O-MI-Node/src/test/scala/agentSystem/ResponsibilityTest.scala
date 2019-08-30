package agentSystem

import java.sql.Timestamp

import akka.actor.ActorRef
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.matcher._
import org.specs2.specification.{Scope,AfterAll}
import scala.collection.immutable._

import agentSystem.AgentResponsibilities._
import types.odf._
import types._
import types.omi._
import testHelpers._

class ResponsibilityTest( implicit ee: ExecutionEnv )  extends Specification with AfterAll{

  implicit val system = Actorstest.createSilentAs()
  def afterAll: Unit ={
    system.terminate
  }
  class ARTest(
    responsibilities: Seq[AgentResponsibility] = Vector.empty,
    odf: ODF = ImmutableODF()
  ) extends ARTestImpl(responsibilities, odf) {}
  class ARTestImpl (
    val responsibilities: Seq[AgentResponsibility] = Vector.empty,
    val odf: ODF = ImmutableODF()
  ) extends Scope{
    val dummyHierarchyStore: ActorRef = DummyHierarchyStore(odf)
    val singleStores = new DummySingleStores(
      hierarchyStore = dummyHierarchyStore
    ) 
    val ar = new AgentResponsibilities(singleStores)
    ar.add(responsibilities)
  }

    def splitRequestTest(
      ar: AgentResponsibilities,
      request: OdfRequest,
      correctResult: Map[Option[Responsible],OdfRequest]
    ) = {
      val result = ar.splitRequestToResponsible( request ) 
      result must haveKeys( correctResult.keys.toSeq:_* ).await
      result must haveValues(correctResult.values.toSeq:_*).await
      //k and v and 
      (result must beEqualTo( correctResult ).await)
    }
    def responsibilityCheckTest(
      ar: AgentResponsibilities,
      agentName: AgentName,
      request: OdfRequest,
      result: Boolean
    ): MatchResult[Boolean] ={
      ar.checkResponsibilityFor( agentName, request) must beEqualTo(result)
    }

  val testTime: Timestamp = Timestamp.valueOf("2018-08-09 16:00:00")
  "AgentResponsibility" should {
    sequential
    "split correctly" >>{
      sequential
      "write request" >> new ARTest(
        Vector( 
          AgentResponsibility( "Writer", Path( "Objects/WritableObj1"), WriteFilter() ),
          AgentResponsibility( "Writer1", Path( "Objects/WritableObj1/II"), WriteFilter() ),
          AgentResponsibility( "Writer0", Path( "Objects/WritableObj0"), ReadFilter() ),
          AgentResponsibility( "Writer2", Path( "Objects/WritableObj2"), ReadWriteFilter() ),
          AgentResponsibility( "Writer3", Path( "Objects/WritableObj3"), WriteCallFilter() ),
          AgentResponsibility( "Writer4", Path( "Objects/WritableObj4"), WriteDeleteFilter() ),
          AgentResponsibility( "Writer5", Path( "Objects/WritableObj5"), ReadWriteCallFilter() ),
          AgentResponsibility( "Writer6", Path( "Objects/WritableObj6"), ReadWriteDeleteFilter() ),
          AgentResponsibility( "Writer7", Path( "Objects/WritableObj7"), WriteCallDeleteFilter() ),
          AgentResponsibility( "Writer8", Path( "Objects/WritableObj8"), ReadWriteCallDeleteFilter() ),
          AgentResponsibility( "WriterN", Path( "Objects/WritableObjN"), ReadDeleteFilter() ),
          AgentResponsibility( "NonWriter", Path( "Objects/NonWritableObj"), ReadFilter())
        )
      ){
        val request: OdfRequest = WriteRequest(
          ImmutableODF(
            Vector(
              InfoItem( Path( "Objects/WritableObj0/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj1/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj2/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj3/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj4/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj5/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj6/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj7/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj8/II"), Vector(IntValue(1,testTime))),
              Object( Path( "Objects/WritableObj9"))
            )
          )
        )

        val correctResult: Map[Option[Responsible],OdfRequest] = Map(
          Some(ResponsibleAgent("Writer1")) -> WriteRequest( 
            ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj1/II"), Vector(IntValue(1,testTime)))))
          ),
           Some(ResponsibleAgent("Writer2")) -> WriteRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj2/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Writer3")) -> WriteRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj3/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Writer4")) -> WriteRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj4/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Writer5")) -> WriteRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj5/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Writer6")) -> WriteRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj6/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Writer7")) -> WriteRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj7/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Writer8")) -> WriteRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj8/II"), Vector(IntValue(1,testTime)))))
           ),
           None -> WriteRequest( 
             ImmutableODF( Vector( Object( Path( "Objects/WritableObj9")),
              InfoItem( Path( "Objects/WritableObj0/II"), Vector(IntValue(1,testTime))) ))
           )
        )
        splitRequestTest(ar, request, correctResult)
      }
      "call request" >> new ARTest(
        Vector( 
          AgentResponsibility( "Caller", Path( "Objects/CallObj1"), CallFilter() ),
          AgentResponsibility( "Caller1", Path( "Objects/CallObj1/II"), CallFilter() ),
          AgentResponsibility( "Caller0", Path( "Objects/CallObj0"), ReadFilter() ),
          AgentResponsibility( "Caller2", Path( "Objects/CallObj2"), ReadCallFilter() ),
          AgentResponsibility( "Caller3", Path( "Objects/CallObj3"), WriteCallFilter() ),
          AgentResponsibility( "Caller4", Path( "Objects/CallObj4"), CallDeleteFilter() ),
          AgentResponsibility( "Caller5", Path( "Objects/CallObj5"), ReadWriteCallFilter() ),
          AgentResponsibility( "Caller6", Path( "Objects/CallObj6"), ReadCallDeleteFilter() ),
          AgentResponsibility( "Caller7", Path( "Objects/CallObj7"), WriteCallDeleteFilter() ),
          AgentResponsibility( "Caller8", Path( "Objects/CallObj8"), ReadWriteCallDeleteFilter() ),
          AgentResponsibility( "CallerN", Path( "Objects/CallObjN"), ReadCallDeleteFilter() ),
          AgentResponsibility( "NonCaller", Path( "Objects/NonCallObj"), ReadFilter())
        )
      ){
        val request: OdfRequest = CallRequest(
          ImmutableODF(
            Vector(
              InfoItem( Path( "Objects/CallObj0/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj1/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj2/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj3/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj4/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj5/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj6/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj7/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj8/II"), Vector(IntValue(1,testTime))),
              Object( Path( "Objects/CallObj9"))
            )
          )
        )

        val correctResult: Map[Option[Responsible],OdfRequest] = Map(
          Some(ResponsibleAgent("Caller1")) -> CallRequest( 
            ImmutableODF( Vector( InfoItem( Path( "Objects/CallObj1/II"), Vector(IntValue(1,testTime)))))
          ),
           Some(ResponsibleAgent("Caller2")) -> CallRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/CallObj2/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Caller3")) -> CallRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/CallObj3/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Caller4")) -> CallRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/CallObj4/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Caller5")) -> CallRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/CallObj5/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Caller6")) -> CallRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/CallObj6/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Caller7")) -> CallRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/CallObj7/II"), Vector(IntValue(1,testTime)))))
           ),
           Some(ResponsibleAgent("Caller8")) -> CallRequest( 
             ImmutableODF( Vector( InfoItem( Path( "Objects/CallObj8/II"), Vector(IntValue(1,testTime)))))
           ),
           None -> CallRequest( 
             ImmutableODF( Vector( 
               Object( Path( "Objects/CallObj9")),
              InfoItem( Path( "Objects/CallObj0/II"), Vector(IntValue(1,testTime)))
              ))
           )
        )
        splitRequestTest(ar, request, correctResult)
      }
      "read request" >> new ARTest(
        Vector( 
          AgentResponsibility( "Reader",  Path( "Objects/ReadObj1"), ReadFilter() ),
          AgentResponsibility( "Reader1", Path( "Objects/ReadObj1/II"), ReadFilter() ),
          AgentResponsibility( "Reader0", Path( "Objects/ReadObj0"), CallFilter() ),
          AgentResponsibility( "Reader2", Path( "Objects/ReadObj2"), ReadCallFilter() ),
          AgentResponsibility( "Reader3", Path( "Objects/ReadObj3"), ReadWriteFilter() ),
          AgentResponsibility( "Reader4", Path( "Objects/ReadObj4"), ReadDeleteFilter() ),
          AgentResponsibility( "Reader5", Path( "Objects/ReadObj5"), ReadWriteCallFilter() ),
          AgentResponsibility( "Reader6", Path( "Objects/ReadObj6"), ReadCallDeleteFilter() ),
          AgentResponsibility( "Reader7", Path( "Objects/ReadObj7"), ReadWriteDeleteFilter() ),
          AgentResponsibility( "Reader8", Path( "Objects/ReadObj8"), ReadWriteCallDeleteFilter() )
        ),
          ImmutableODF(
            Vector(
              InfoItem( Path( "Objects/ReadObj0/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj1/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj1/II2"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj2/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj3/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj4/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj5/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj6/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj7/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/ReadObj8/II"), Vector(IntValue(1,testTime))),
              Object( Path( "Objects/ReadObj9"))
            )
          )
      ){
        val request: OdfRequest = ReadRequest(
          ImmutableODF(
            Objects()
          )
        )

        val correctResult: Map[Option[Responsible],OdfRequest] = Map(
          Some(ResponsibleAgent("Reader")) -> ReadRequest( 
            ImmutableODF( Vector( InfoItem( Path( "Objects/ReadObj1/II2"), Vector.empty)))
          ),
          Some(ResponsibleAgent("Reader1")) -> ReadRequest( 
            ImmutableODF( Vector( InfoItem( Path( "Objects/ReadObj1/II"), Vector.empty)))
          ),
          Some(ResponsibleAgent("Reader2")) -> ReadRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/ReadObj2"))))
          ),
          Some(ResponsibleAgent("Reader3")) -> ReadRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/ReadObj3"))))
          ),
          Some(ResponsibleAgent("Reader4")) -> ReadRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/ReadObj4"))))
          ),
          Some(ResponsibleAgent("Reader5")) -> ReadRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/ReadObj5"))))
          ),
          Some(ResponsibleAgent("Reader6")) -> ReadRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/ReadObj6"))))
          ),
          Some(ResponsibleAgent("Reader7")) -> ReadRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/ReadObj7"))))
          ),
          Some(ResponsibleAgent("Reader8")) -> ReadRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/ReadObj8"))))
          ),
           None -> ReadRequest( 
             ImmutableODF( Vector( 
               Object( Path( "Objects/ReadObj9")),
               Object( Path( "Objects/ReadObj0"))
              ))
           )
        )
        splitRequestTest(ar, request, correctResult)
      }
      "delete request" >> new ARTest(
        Vector( 
          AgentResponsibility( "Deleter",  Path( "Objects/DeleteObj1"), DeleteFilter() ),
          AgentResponsibility( "Deleter1", Path( "Objects/DeleteObj1/II"), DeleteFilter() ),
          AgentResponsibility( "Deleter0", Path( "Objects/DeleteObj0"), CallFilter() ),
          AgentResponsibility( "Deleter2", Path( "Objects/DeleteObj2"), CallDeleteFilter() ),
          AgentResponsibility( "Deleter3", Path( "Objects/DeleteObj3"), WriteDeleteFilter() ),
          AgentResponsibility( "Deleter4", Path( "Objects/DeleteObj4"), ReadDeleteFilter() ),
          AgentResponsibility( "Deleter5", Path( "Objects/DeleteObj5"), WriteCallDeleteFilter() ),
          AgentResponsibility( "Deleter6", Path( "Objects/DeleteObj6"), ReadCallDeleteFilter() ),
          AgentResponsibility( "Deleter7", Path( "Objects/DeleteObj7"), ReadWriteDeleteFilter() ),
          AgentResponsibility( "Deleter8", Path( "Objects/DeleteObj8"), ReadWriteCallDeleteFilter() )
        ),
          ImmutableODF(
            Vector(
              InfoItem( Path( "Objects/DeleteObj0/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj1/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj1/II2"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj2/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj3/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj4/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj5/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj6/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj7/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/DeleteObj8/II"), Vector(IntValue(1,testTime))),
              Object( Path( "Objects/DeleteObj9"))
            )
          )
      ){
        val request: OdfRequest = DeleteRequest(
          ImmutableODF(
            Objects()
          )
        )

        val correctResult: Map[Option[Responsible],OdfRequest] = Map(
          Some(ResponsibleAgent("Deleter")) -> DeleteRequest( 
            ImmutableODF( Vector( InfoItem( Path( "Objects/DeleteObj1/II2"), Vector.empty)))
          ),
          Some(ResponsibleAgent("Deleter1")) -> DeleteRequest( 
            ImmutableODF( Vector( InfoItem( Path( "Objects/DeleteObj1/II"), Vector.empty)))
          ),
          Some(ResponsibleAgent("Deleter2")) -> DeleteRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/DeleteObj2"))))
          ),
          Some(ResponsibleAgent("Deleter3")) -> DeleteRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/DeleteObj3"))))
          ),
          Some(ResponsibleAgent("Deleter4")) -> DeleteRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/DeleteObj4"))))
          ),
          Some(ResponsibleAgent("Deleter5")) -> DeleteRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/DeleteObj5"))))
          ),
          Some(ResponsibleAgent("Deleter6")) -> DeleteRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/DeleteObj6"))))
          ),
          Some(ResponsibleAgent("Deleter7")) -> DeleteRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/DeleteObj7"))))
          ),
          Some(ResponsibleAgent("Deleter8")) -> DeleteRequest( 
            ImmutableODF( Vector( Object( Path( "Objects/DeleteObj8"))))
          ),
           None -> DeleteRequest( 
             ImmutableODF( Vector( 
               Object( Path( "Objects/DeleteObj0")),
               Object( Path( "Objects/DeleteObj9"))
              ))
           )
        )
        splitRequestTest(ar, request, correctResult)
      }
    }
    "check responsiblity correctly for" >>{
      
      "write request" >> new ARTest(
        Vector( 
          AgentResponsibility( "Writer", Path( "Objects/WritableObj1"), WriteFilter() ),
          AgentResponsibility( "Writer1", Path( "Objects/WritableObj1/II"), WriteFilter() ),
          AgentResponsibility( "Writer2", Path( "Objects/WritableObj2"), ReadWriteFilter() ),
          AgentResponsibility( "Writer3", Path( "Objects/WritableObj3"), WriteCallFilter() ),
          AgentResponsibility( "Writer4", Path( "Objects/WritableObj4"), WriteDeleteFilter() ),
          AgentResponsibility( "Writer5", Path( "Objects/WritableObj5"), ReadWriteCallFilter() ),
          AgentResponsibility( "Writer6", Path( "Objects/WritableObj6"), ReadWriteDeleteFilter() ),
          AgentResponsibility( "Writer7", Path( "Objects/WritableObj7"), WriteCallDeleteFilter() ),
          AgentResponsibility( "Writer8", Path( "Objects/WritableObj8"), ReadWriteCallDeleteFilter() ),
          AgentResponsibility( "WriterN", Path( "Objects/WritableObjN"), ReadWriteDeleteFilter() ),
          AgentResponsibility( "NonWriter", Path( "Objects/NonWritableObj"), ReadFilter())
        )
      ){
        val request = WriteRequest(
          ImmutableODF( Vector(
              InfoItem( Path( "Objects/WritableObjAUAUUA/II1"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj2/II1"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj2/II2"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj2/II3"), Vector(IntValue(1,testTime)))
          ))
        )
        responsibilityCheckTest(ar, "Writer2", request, true) and 
        responsibilityCheckTest(ar, "Unknown", request, false) and 
        responsibilityCheckTest(ar, "", request, false) 
      } 
      "call request" >> new ARTest(
        Vector( 
          AgentResponsibility( "Caller", Path( "Objects/CallObj1"), CallFilter() ),
          AgentResponsibility( "Caller1", Path( "Objects/CallObj1/II"), CallFilter() ),
          AgentResponsibility( "Caller2", Path( "Objects/CallObj2"), ReadCallFilter() ),
          AgentResponsibility( "Caller3", Path( "Objects/CallObj3"), WriteCallFilter() ),
          AgentResponsibility( "Caller4", Path( "Objects/CallObj4"), CallDeleteFilter() ),
          AgentResponsibility( "Caller5", Path( "Objects/CallObj5"), ReadWriteCallFilter() ),
          AgentResponsibility( "Caller6", Path( "Objects/CallObj6"), ReadCallDeleteFilter() ),
          AgentResponsibility( "Caller7", Path( "Objects/CallObj7"), WriteCallDeleteFilter() ),
          AgentResponsibility( "Caller8", Path( "Objects/CallObj8"), ReadWriteCallDeleteFilter() ),
          AgentResponsibility( "CallerN", Path( "Objects/CallObjN"), ReadCallDeleteFilter() ),
          AgentResponsibility( "NonCaller", Path( "Objects/NonCallObj"), ReadFilter())
        )
      ){
        val request = CallRequest(
          ImmutableODF( Vector(
              InfoItem( Path( "Objects/CallObjAUAUUA/II1"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj2/II1"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj2/II2"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/CallObj2/II3"), Vector(IntValue(1,testTime)))
          ))
        )
        responsibilityCheckTest(ar, "Caller2", request, true) and 
        responsibilityCheckTest(ar, "Unknown", request, false) and
        responsibilityCheckTest(ar, "", request, false) 
      } 
      /*
      "read request" >> {
        1 === 2
      }
      "delete request" >> {
        1 === 2
      }*/
    } 
  }
}
