package controllers

import models._
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, Controller}
import scalikejdbc.DB

import scala.concurrent.Future
import scalaoauth2.provider._
import scalaoauth2.provider.OAuth2ProviderActionBuilders._

class OAuth2Controller extends Controller with OAuth2Provider {

  implicit val value = new Writes[AuthInfo[UserAccount]] {
    def writes(authInfo: AuthInfo[UserAccount]) = {
      Json.obj(
        "accountInfo" -> Json.obj(
          "emailId" -> authInfo.user.email
        ),
        "oauthClientId" -> authInfo.clientId,
        "redirectUri" -> authInfo.redirectUri
      )
    }
  }

  override val tokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode(),
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken(),
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials(),
      OAuthGrantType.PASSWORD -> new Password()
    )
  }

  def generateAccessToken = Action.async { implicit request =>
    issueAccessToken(new MyDataHandler())
  }

  def getResources = AuthorizedAction(new MyDataHandler()) { request =>
    Ok(Json.toJson(request.authInfo))
  }

  class MyDataHandler extends DataHandler[UserAccount] {

    override def validateClient(maybeCredential: Option[ClientCredential], request: AuthorizationRequest): Future[Boolean] = DB.readOnly { implicit session =>
      Future.successful((for {
        clientCredential <- maybeCredential
      } yield Oauth2Client.validate(clientCredential.clientId, clientCredential.clientSecret.getOrElse(""), request.grantType)).contains(true))
    }

    override def getStoredAccessToken(authInfo: AuthInfo[UserAccount]): Future[Option[AccessToken]] = DB.readOnly { implicit session =>
      Future.successful(Oauth2AccessToken.findByAuthorized(authInfo.user, authInfo.clientId.getOrElse("")).map(toAccessToken))
    }

    override def createAccessToken(authInfo: AuthInfo[UserAccount]): Future[AccessToken] = DB.localTx { implicit session =>
      val clientId = authInfo.clientId.getOrElse(throw new InvalidClient())
      val oauthClient = Oauth2Client.findByClientId(clientId).getOrElse(throw new InvalidClient())
      val accessToken = Oauth2AccessToken.create(authInfo.user, oauthClient)
      Future.successful(toAccessToken(accessToken))
    }

    private val accessTokenExpireSeconds = 3600
    private def toAccessToken(accessToken: Oauth2AccessToken) = {
      AccessToken(
        accessToken.accessToken,
        Some(accessToken.refreshToken),
        None,
        Some(accessTokenExpireSeconds),
        accessToken.createdAt.toDate
      )
    }

    override def findUser(maybeCredential: Option[ClientCredential], request: AuthorizationRequest): Future[Option[UserAccount]] = DB.readOnly { implicit session =>
      request match {
        case request: PasswordRequest =>
          Future.successful(UserAccount.authenticate(request.username, request.password))
        case request: ClientCredentialsRequest =>
          Future.successful {
            for {
              clientCredential <- maybeCredential
              account <- Oauth2Client.findClientCredentials(
                clientCredential.clientId,
                clientCredential.clientSecret.getOrElse("")
              )
            } yield account
          }
        case _ =>
          Future.successful(None)
      }
    }

    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[UserAccount]]] = DB.readOnly { implicit session =>
      Future.successful(Oauth2AccessToken.findByRefreshToken(refreshToken).flatMap { accessToken =>
        for {
          account <- accessToken.account
          client <- accessToken.oauthClient
        } yield {
          AuthInfo(
            user = account,
            clientId = Some(client.clientId),
            scope = None,
            redirectUri = None
          )
        }
      })
    }

    override def refreshAccessToken(authInfo: AuthInfo[UserAccount], refreshToken: String): Future[AccessToken] = DB.localTx { implicit session =>
      val clientId = authInfo.clientId.getOrElse(throw new InvalidClient())
      val client = Oauth2Client.findByClientId(clientId).getOrElse(throw new InvalidClient())
      val accessToken = Oauth2AccessToken.refresh(authInfo.user, client)
      Future.successful(toAccessToken(accessToken))
    }

    // Authorization code grant

    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[UserAccount]]] = DB.readOnly { implicit session =>
      Future.successful(Oauth2AuthorizationCode.findByCode(code).flatMap { authorization =>
        for {
          account <- authorization.account
          client <- authorization.oauthClient
        } yield {
          AuthInfo(
            user = account,
            clientId = Some(client.clientId),
            scope = None,
            redirectUri = authorization.redirectUri
          )
        }
      })
    }

    override def deleteAuthCode(code: String): Future[Unit] = DB.localTx { implicit session =>
      Future.successful(Oauth2AuthorizationCode.delete(code))
    }

    override def findAccessToken(token: String): Future[Option[AccessToken]] = DB.readOnly { implicit session =>
      Future.successful(Oauth2AccessToken.findByAccessToken(token).map(toAccessToken))
    }

    override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[UserAccount]]] = DB.readOnly { implicit session =>
      Future.successful(Oauth2AccessToken.findByAccessToken(accessToken.token).flatMap { case accessToken =>
        for {
          account <- accessToken.account
          client <- accessToken.oauthClient
        } yield {
          AuthInfo(
            user = account,
            clientId = Some(client.clientId),
            scope = None,
            redirectUri = client.redirectUri
          )
        }
      })
    }
  }
}
