package agentSystem

import java.sql.Timestamp

import org.specs2.mutable._
import org.specs2.matcher._
import scala.collection.immutable._

import agentSystem.AgentResponsibilities._
import types.odf._
import types._
import types.OmiTypes._
import http.{OmiConfig, OmiConfigExtension}
import testHelpers._

class ResponsibilityTest extends Specification{

  class ARTest(
    val responsibilities: Seq[AgentResponsibility] = Vector.empty,
    val odf: ODF = ImmutableODF()
  ) extends Actorstest{
    val settings: OmiConfigExtension = OmiConfig(system)
    val singleStores = new DummySingleStores(
      settings,
      hierarchyStore= DummyHierarchyStore(odf)
    ) 
    val ar = new AgentResponsibilities()
    ar.add(responsibilities)
   
  }

  def splitRequestTest(
    ar: AgentResponsibilities,
    request: OdfRequest,
    correctResult: Map[Option[Responsible],OdfRequest]
  ): MatchResult[Map[Option[Responsible],OdfRequest]]= {
    ar.splitRequestToResponsible( request ) must beEqualTo( correctResult ) 
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
    "split correctly" >>{
      "write request" >> new ARTest(
        Vector( 
          AgentResponsibility( "Writer1", Path( "Objects/WritableObj1"), WriteFilter() ),
          AgentResponsibility( "Writer2", Path( "Objects/WritableObj2"), ReadWriteFilter() ),
          AgentResponsibility( "Writer3", Path( "Objects/WritableObj3"), WriteCallFilter() ),
          AgentResponsibility( "Writer4", Path( "Objects/WritableObj4"), WriteDeleteFilter() ),
          AgentResponsibility( "Writer5", Path( "Objects/WritableObj5"), ReadWriteCallFilter() ),
          AgentResponsibility( "Writer6", Path( "Objects/WritableObj6"), ReadWriteDeleteFilter() ),
          AgentResponsibility( "Writer7", Path( "Objects/WritableObj7"), WriteCallDeleteFilter() ),
          AgentResponsibility( "Writer8", Path( "Objects/WritableObj8"), ReadWriteCallDeleteFilter() ),
          AgentResponsibility( "NonWriter", Path( "Objects/NonWritableObj"), ReadFilter())
        )
      ){
        val request: OdfRequest = WriteRequest(
          ImmutableODF(
            Vector(
              InfoItem( Path( "Objects/WritableObj1/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj2/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj3/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj4/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj5/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj6/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj7/II"), Vector(IntValue(1,testTime))),
              InfoItem( Path( "Objects/WritableObj8/II"), Vector(IntValue(1,testTime)))
            )
          )
        )

        val correctResult: Map[Option[Responsible],OdfRequest] = Map(
          Some(ResponsibleAgent("Writer1")) -> WriteRequest( ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj1/II"), Vector(IntValue(1,testTime)))))),
           Some(ResponsibleAgent("Writer2")) -> WriteRequest( ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj2/II"), Vector(IntValue(1,testTime)))))),
           Some(ResponsibleAgent("Writer3")) -> WriteRequest( ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj3/II"), Vector(IntValue(1,testTime)))))),
           Some(ResponsibleAgent("Writer4")) -> WriteRequest( ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj4/II"), Vector(IntValue(1,testTime)))))),
           Some(ResponsibleAgent("Writer5")) -> WriteRequest( ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj5/II"), Vector(IntValue(1,testTime)))))),
           Some(ResponsibleAgent("Writer6")) -> WriteRequest( ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj6/II"), Vector(IntValue(1,testTime)))))),
           Some(ResponsibleAgent("Writer7")) -> WriteRequest( ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj7/II"), Vector(IntValue(1,testTime)))))),
           Some(ResponsibleAgent("Writer8")) -> WriteRequest( ImmutableODF( Vector( InfoItem( Path( "Objects/WritableObj8/II"), Vector(IntValue(1,testTime))))))
        )
        splitRequestTest(ar, request, correctResult)
      }
      /*
      "call request" >>{
        1 === 2
      }
      "delete request" >>{
        1 === 2
      }
      "read request" >>{
        1 === 2
      }*/
    }
    /*
    "check responsiblity correctly for" >>{
      
      
      "write request" >> {
        1 === 2
      } 
      "call request" >> {
        1 === 2
      }
      "read request" >> {
        1 === 2
      }
      "delete request" >> {
        1 === 2
      }
    }*/ 
  }
}
