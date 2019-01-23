package org.thp.cortex.services

import javax.inject.{ Inject, Singleton }

import akka.stream.Materializer
import org.elastic4play.services.{ AuthContext, AuthSrv, Role }
import org.elastic4play.{ AuthenticationError, AuthorizationError, OAuth2Redirect }
import play.api.http.Status
import play.api.libs.json.{ JsObject, JsValue }
import play.api.libs.ws.WSClient
import play.api.mvc.RequestHeader
import play.api.{ Configuration, Logger }
import org.thp.cortex.services.UserSrv
import services.mappers.UserMapper

import scala.concurrent.{ ExecutionContext, Future }

case class OAuth2Config(
    clientId: Option[String] = None,
    clientSecret: String,
    redirectUri: String,
    responseType: String,
    grantType: String,
    authorizationUrl: String,
    tokenUrl: String,
    userUrl: String,
    scope: String,
    autocreate: Boolean)

object OAuth2Config {
  def apply(configuration: Configuration): OAuth2Config = {
    (for {
      clientId ← configuration.getOptional[String]("auth.oauth2.clientId")
      clientSecret ← configuration.getOptional[String]("auth.oauth2.clientSecret")
      redirectUri ← configuration.getOptional[String]("auth.oauth2.redirectUri")
      responseType ← configuration.getOptional[String]("auth.oauth2.responseType")
      grantType ← configuration.getOptional[String]("auth.oauth2.grantType")
      authorizationUrl ← configuration.getOptional[String]("auth.oauth2.authorizationUrl")
      userUrl ← configuration.getOptional[String]("auth.oauth2.userUrl")
      tokenUrl ← configuration.getOptional[String]("auth.oauth2.tokenUrl")
      scope ← configuration.getOptional[String]("auth.oauth2.scope")
      autocreate ← configuration.getOptional[Boolean]("auth.sso.autocreate").orElse(Some(false))
    } yield OAuth2Config(Some(clientId), clientSecret, redirectUri, responseType, grantType, authorizationUrl, tokenUrl, userUrl, scope, autocreate))
      .getOrElse(OAuth2Config(tokenUrl = "", clientSecret = "", redirectUri = "", responseType = "", grantType = "", authorizationUrl = "", userUrl = "", scope = "", autocreate = false))
  }
}

@Singleton
class OAuth2Srv(
    ws: WSClient,
    userSrv: UserSrv,
    ssoMapper: UserMapper,
    oauth2Config: OAuth2Config,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer)
  extends AuthSrv {

  @Inject() def this(
      ws: WSClient,
      ssoMapper: UserMapper,
      userSrv: UserSrv,
      configuration: Configuration,
      ec: ExecutionContext,
      mat: Materializer) = this(
    ws,
    userSrv,
    ssoMapper,
    OAuth2Config(configuration),
    ec,
    mat)

  override val name: String = "oauth2"
  private val logger = Logger(classOf[OAuth2Srv]).logger

  val Oauth2TokenQueryString = "code"

  override def authenticate()(implicit request: RequestHeader): Future[AuthContext] = {
    oauth2Config.clientId
      .fold[Future[AuthContext]](Future.failed(AuthenticationError("OAuth2 not configured properly"))) {
        clientId ⇒
          request.queryString.get(Oauth2TokenQueryString).flatMap(_.headOption) match {
            case Some(code) ⇒
              getAuthTokenAndAuthenticate(clientId, code)
            case None ⇒
              createOauth2Redirect(clientId)
          }
      }
  }

  private def getAuthTokenAndAuthenticate(clientId: String, code: String)(implicit request: RequestHeader): Future[AuthContext] = {
    logger.debug("Getting user token with the code from the response!")
    ws.url(oauth2Config.tokenUrl)
      .post(Map(
        "code" -> code,
        "grant_type" -> oauth2Config.grantType,
        "client_secret" -> oauth2Config.clientSecret,
        "redirect_uri" -> oauth2Config.redirectUri,
        "client_id" -> clientId))
      .flatMap { r ⇒
        r.status match {
          case Status.OK ⇒
            val accessToken = (r.json \ "access_token").asOpt[String].getOrElse("")
            val authHeader = "Authorization" -> s"Bearer $accessToken"
            ws.url(oauth2Config.userUrl)
              .addHttpHeaders(authHeader)
              .get().flatMap { userResponse ⇒
                if (userResponse.status != Status.OK) {
                  Future.failed(AuthenticationError("unexpected response from server"))
                }
                else {
                  val response = userResponse.json.asInstanceOf[JsObject]
                  getOrCreateUser(response, authHeader)
                }
              }
          case _ ⇒ Future.failed(AuthenticationError("unexpected response from server"))
        }
      }
  }

  private def getOrCreateUser(response: JsValue, authHeader: (String, String))(implicit request: RequestHeader): Future[AuthContext] = {
    ssoMapper.getUserFields(response, Some(authHeader)).flatMap {
      userFields ⇒
        val userId = userFields.getString("login").getOrElse("")
        userSrv.get(userId).flatMap(user ⇒ {
          userSrv.getFromUser(request, user)
        }).recoverWith {
          case authErr: AuthorizationError ⇒ Future.failed(authErr)
          case err if oauth2Config.autocreate ⇒
            implicit val fakeAuthContext: AuthContext = new AuthContext {
              override def roles: Seq[Role] = Seq()
              override def userName: String = ""
              override def userId: String = ""
              override def requestId: String = ""
            }
            userSrv.create(userFields).flatMap(user ⇒ {
              userSrv.getFromUser(request, user)
            })
          case err ⇒ Future.failed(err)
        }
    }
  }

  private def createOauth2Redirect(clientId: String) = {
    val queryStringParams = Map[String, Seq[String]](
      "scope" -> Seq(oauth2Config.scope),
      "response_type" -> Seq(oauth2Config.responseType),
      "redirect_uri" -> Seq(oauth2Config.redirectUri),
      "client_id" -> Seq(clientId))
    Future.failed(OAuth2Redirect(oauth2Config.authorizationUrl, queryStringParams))
  }
}
