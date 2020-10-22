package models

import java.security.SecureRandom

import org.joda.time.DateTime
import scalikejdbc._
import skinny.orm.SkinnyCRUDMapper

import scala.util.Random

case class Oauth2AccessToken(
                             id: Long,
                             userAccountId: Long,
                             account: Option[UserAccount] = None,
                             oauth2ClientId: Long,
                             oauthClient: Option[Oauth2Client] = None,
                             accessToken: String,
                             refreshToken: String,
                             createdAt: DateTime
)

object Oauth2AccessToken extends SkinnyCRUDMapper[Oauth2AccessToken] {

  override val tableName = "oauth2_access_token"
  override def defaultAlias = createAlias("oat")

  belongsTo[UserAccount](UserAccount, (oat, account) => oat.copy(account = account)).byDefault
  belongsTo[Oauth2Client](Oauth2Client, (oat, client) => oat.copy(oauthClient = client)).byDefault

  override def extract(rs: WrappedResultSet, oat: ResultName[Oauth2AccessToken]) = new Oauth2AccessToken(
    id = rs.long(oat.id),
    userAccountId = rs.long(oat.userAccountId),
    oauth2ClientId = rs.long(oat.oauth2ClientId),
    accessToken = rs.string(oat.accessToken),
    refreshToken = rs.string(oat.refreshToken),
    createdAt = rs.jodaDateTime(oat.createdAt)
  )

  def create(account: UserAccount, client: Oauth2Client)(implicit session: DBSession): Oauth2AccessToken = {
    def randomString(length: Int) = new Random(new SecureRandom()).alphanumeric.take(length).mkString
    val accessToken = randomString(40)
    val refreshToken = randomString(40)
    val createdAt = new DateTime()

    val oauthAccessToken = new Oauth2AccessToken(
      id = 0,
      userAccountId = account.id,
      oauth2ClientId = client.id,
      accessToken = accessToken,
      refreshToken = refreshToken,
      createdAt = createdAt
    )

    val generatedId = Oauth2AccessToken.createWithNamedValues(
      column.userAccountId -> oauthAccessToken.userAccountId,
      column.oauth2ClientId -> oauthAccessToken.oauth2ClientId,
      column.accessToken -> oauthAccessToken.accessToken,
      column.refreshToken -> oauthAccessToken.refreshToken,
      column.createdAt -> oauthAccessToken.createdAt
    )
    oauthAccessToken.copy(id = generatedId)
  }

  def delete(account: UserAccount, client: Oauth2Client)(implicit session: DBSession): Int = {
    Oauth2AccessToken.deleteBy(sqls
      .eq(column.userAccountId, account.id).and
      .eq(column.oauth2ClientId, client.id)
    )
  }

  def refresh(account: UserAccount, client: Oauth2Client)(implicit session: DBSession): Oauth2AccessToken = {
    delete(account, client)
    create(account, client)
  }

  def findByAccessToken(accessToken: String)(implicit session: DBSession): Option[Oauth2AccessToken] = {
    val oat = Oauth2AccessToken.defaultAlias
    Oauth2AccessToken.where(sqls.eq(oat.accessToken, accessToken)).apply().headOption
  }

  def findByAuthorized(account: UserAccount, clientId: String)(implicit session: DBSession): Option[Oauth2AccessToken] = {
    val oat = Oauth2AccessToken.defaultAlias
    val oac = Oauth2Client.defaultAlias
    Oauth2AccessToken.where(sqls
      .eq(oat.userAccountId, account.id).and
      .eq(oac.clientId, clientId)
    ).apply().headOption
  }

  def findByRefreshToken(refreshToken: String)(implicit session: DBSession): Option[Oauth2AccessToken] = {
    val expireAt = new DateTime().minusMonths(1)
    val oat = Oauth2AccessToken.defaultAlias
    Oauth2AccessToken.where(sqls
      .eq(oat.refreshToken, refreshToken).and
      .gt(oat.createdAt, expireAt)
    ).apply().headOption
  }
}
