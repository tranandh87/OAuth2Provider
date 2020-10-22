package models

import java.security.MessageDigest

import org.joda.time.DateTime
import scalikejdbc._
import skinny.orm._

case class UserAccount(id: Long, email: String, password: String, createdAt: DateTime)
object UserAccount extends SkinnyCRUDMapper[UserAccount] {

  override val tableName = "user_account"
  override def defaultAlias = createAlias("a")
  val ownerAlias = createAlias("owner")

  override def extract(rs: WrappedResultSet, a: ResultName[UserAccount]) = new UserAccount(
    id = rs.get(a.id),
    email = rs.get(a.email),
    password = rs.get(a.password),
    createdAt = rs.get(a.createdAt)
  )

  private def digestString(s: String): String = {
    val md = MessageDigest.getInstance("SHA-1")
    md.update(s.getBytes)
    md.digest.foldLeft("") { (s, b) =>
      s + "%02x".format(if (b < 0) b + 256 else b)
    }
  }

  def authenticate(email: String, password: String)(implicit s: DBSession): Option[UserAccount] = {
    val hashedPassword = digestString(password)
    val a = UserAccount.defaultAlias
    UserAccount.where(sqls.eq(a.email, email).and.eq(a.password, hashedPassword)).apply().headOption
  }
}
