package models

import models.Oauth2AccessToken._
import org.joda.time.DateTime
import scalikejdbc._
import skinny.orm.SkinnyCRUDMapper

case class Oauth2AuthorizationCode(
                                   id: Long,
                                   userAccountId: Long,
                                   account: Option[UserAccount] = None,
                                   oauth2ClientId: Long,
                                   oauthClient: Option[Oauth2Client] = None,
                                   code: String,
                                   redirectUri: Option[String],
                                   createdAt: DateTime)

object Oauth2AuthorizationCode extends SkinnyCRUDMapper[Oauth2AuthorizationCode] {

  override val tableName = "oauth2_authorization_code"
  override def defaultAlias = createAlias("oac")

  belongsTo[UserAccount](UserAccount, (oac, account) => oac.copy(account = account)).byDefault
  belongsTo[Oauth2Client](Oauth2Client, (oac, client) => oac.copy(oauthClient = client)).byDefault

  override def extract(rs: WrappedResultSet, oac: ResultName[Oauth2AuthorizationCode]) = new Oauth2AuthorizationCode(
    id = rs.long(oac.id),
    userAccountId = rs.long(oac.userAccountId),
    oauth2ClientId = rs.long(oac.oauth2ClientId),
    code = rs.string(oac.code),
    redirectUri = rs.stringOpt(oac.redirectUri),
    createdAt = rs.jodaDateTime(oac.createdAt)
  )

  def findByCode(code: String)(implicit session: DBSession): Option[Oauth2AuthorizationCode] = {
    val oac = Oauth2AuthorizationCode.defaultAlias
    val expireAt = new DateTime().minusMinutes(30)
    Oauth2AuthorizationCode.where(
      sqls
        .eq(oac.code, code).and
        .gt(oac.createdAt, expireAt)
    ).apply().headOption
  }

  def delete(code: String)(implicit session: DBSession): Unit = {
    Oauth2AuthorizationCode.deleteBy(sqls
      .eq(column.code, code)
    )
  }
}
