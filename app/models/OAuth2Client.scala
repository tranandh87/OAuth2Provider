package models

import org.joda.time.DateTime
import scalikejdbc._
import skinny.orm._

case class Oauth2Client(
                        id: Long,
                        ownerId: Long,
                        owner: Option[UserAccount] = None,
                        grantType: String,
                        clientId: String,
                        clientSecret: String,
                        redirectUri: Option[String],
                        createdAt: DateTime
)

object Oauth2Client extends SkinnyCRUDMapper[Oauth2Client] {

  override val tableName = "oauth2_client"
  override def defaultAlias = createAlias("oc")

  override def extract(rs: WrappedResultSet, oc: ResultName[Oauth2Client]) = new Oauth2Client(
    id = rs.long(oc.id),
    ownerId = rs.long(oc.ownerId),
    grantType = rs.string(oc.grantType),
    clientId = rs.string(oc.clientId),
    clientSecret = rs.string(oc.clientSecret),
    redirectUri = rs.stringOpt(oc.redirectUri),
    createdAt = rs.jodaDateTime(oc.createdAt)
  )

  innerJoinWithDefaults(UserAccount, (c, owner) => sqls.eq(c.ownerId, owner.id)).byDefaultEvenIfAssociated
  val owner = belongsToWithAlias[UserAccount](UserAccount -> UserAccount.ownerAlias, (c, owner) => c.copy(owner = owner)).byDefault

  def validate(clientId: String, clientSecret: String, grantType: String)(implicit s: DBSession): Boolean = {
    val oc = Oauth2Client.defaultAlias
    Oauth2Client.where(sqls
      .eq(oc.clientId, clientId).and
      .eq(oc.clientSecret, clientSecret)
    ).apply().headOption.map { client =>
      grantType == client.grantType || grantType == "refresh_token"
    }.getOrElse(false)
  }

  def findByClientId(clientId: String)(implicit s: DBSession): Option[Oauth2Client] = {
    val oc = Oauth2Client.defaultAlias
    Oauth2Client.where(sqls
      .eq(oc.clientId, clientId)
    ).apply().headOption
  }

  def findClientCredentials(clientId: String, clientSecret: String)(implicit session: DBSession): Option[UserAccount] = {
    val oc = Oauth2Client.defaultAlias
    Oauth2Client.where(sqls
      .eq(oc.clientId, clientId).and
      .eq(oc.clientSecret, clientSecret).and
      .eq(oc.grantType, "client_credentials")
    ).apply().headOption.flatMap { _.owner }
  }
}
