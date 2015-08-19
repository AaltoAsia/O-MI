package http

import spray.http._
import spray.routing._
import Directives._

import types.OmiTypes._


/**
 * This trait defines the base interface for authorization implementations, based on
 * http headers or other http request data. All usages should extend AuthorizationSupportBase
 * before this for the support of combining many authorization implementations.
 *
 * Implementations should extend [[AuthorizationExtension]] trait.
 */
sealed trait Authorization[UserData] {

  /** This directive gets the user identification data from the request.
   */
  def extractUserData: Directive1[UserData]

  /** Tests if user specified by [[UserData]] has permission for request [[OmiRequest]].
   * Function is in curried format.
   *
   * @return Boolean, true if connection is permited to do input.
   */
  def hasPermission: UserData => OmiRequest => Boolean

  def userToString: UserData => String

}



/** 
 *  Base trait for authorization in Stackable trait pattern.
 *  (http://www.artima.com/scalazine/articles/stackable_trait_pattern.html)
 *  
 *  Extend this before any [[AuthorizationExtension]].
 */
trait AuthorizationExtSupport {
  def makePermissionTestFunction: Directive1[OmiRequest => Boolean]
}

/** 
 *  Core trait for authorization support in Stackable trait pattern.
 *  One of these need to be extended before stackable extension traits.
 *  Grants all permissions for all users.
 */
trait ExtensibleAuthorization extends AuthorizationExtSupport {
  /**
   * This directive is supposed to extract all required data for any user authorization.
   * The function extracted takes a OmiRequest and returns a Boolean indicating whether
   * the user is a valid user and authorized for the given request.
   *
   * The function can be used many times for the same user.
   *
   * NOTE: Put this as up in routing DSL as possible because some extractors seem to not
   * working properly otherwise.
   */
  def makePermissionTestFunction =
    provide(_ => true)
    //extractUserData map hasPermission
}


/**
 * Template for any authorization implementations. This enables the combination of many
 * authorization methods in the service using [[makePermissionTestFunction]] that combines
 * all [[hasPermission]] functions.
 */
trait AuthorizationExtension[T] extends AuthorizationExtSupport with Authorization[T] {
  /** Abstract override of Stackable trait pattern; Combines other traits' functionality */
  abstract override def makePermissionTestFunction = 
    for {
      otherTest <- super.makePermissionTestFunction
      ourTest   <- extractUserData map hasPermission

      combinedTest = (request: OmiRequest) =>
        otherTest(request) || ourTest(request)

    } yield combinedTest
}


/** Dummy authorization, allows everything. Can be used for testing, disabling authorization
 *  temporarily and serves as an example of how to extend [[Authorization]] as a Stackable trait.
 */
trait AllowAllAuthorization extends AuthorizationExtension[Unit] {
  def extractUserData = provide(())
  def hasPermission   = _ => _ => true
  def userToString    = _ => "TEST-USER"
}
