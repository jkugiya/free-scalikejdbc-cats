package scalikejdbc.cats.free

import cats._
import cats.free.Free
import cats.implicits._
import scalikejdbc._
import scalikejdbc.cats.free.Interpreter.TesterBuffer
import scalikejdbc.config._

object TestMain extends App {

  private val a  = Account.syntax("a")
  private val ac = Account.column

  def program[F[_]](implicit S: ScalikeJDBC[F]) = {
    import S._
    for {
      _  <- execute(sql"CREATE TABLE account (id SERIAL PRIMARY KEY, name TEXT NOT NULL)")
      i1 <- generateKey(sql"INSERT INTO account (name) VALUES ('Alice')")
      i2 <- generateKey(insert.into(Account).namedValues(ac.name -> "Bob"))
      l1 <- vector(sql"SELECT ${a.result.*} FROM ${Account.as(a)}".map(Account(a)))
      l2 <- vector(select.from(Account.as(a)))(Account(a))
      o1 <- first(sql"SELECT * FROM account ORDER BY id".map(_.int("id")))
      o2 <- first(select.from(Account.as(a)).orderBy(a.id))(_.int(a.resultName.id))
      n1 <- foldLeft(sql"SELECT name FROM account")("") { (s, rs) =>
        s ++ rs.get[String](1)
      }
      n2 <- foldLeft(select.from(Account.as(a)))("") { (s, rs) =>
        s ++ Account(a)(rs).name
      }
    } yield (i1, l1, o1, n1)
  }

  def testApp = program[Query].foldMap(Interpreter.transaction)

  def failPg[F[_]](implicit S: ScalikeJDBC[F]) = {
    import S._
    for {
      l <- vector(sql"SELECT foooo FROM account".map(_.get[Int](1)))
      o <- first(sql"SELECT id FROM account ORDER BY id".map(_.get[Int](1)))
    } yield (l, o)
  }

  def failApp = failPg[Query].foldMap(Interpreter.safeTransaction)

  implicit def sqlEitherTxBoundary[A] = new TxBoundary[Interpreter.SQLEither[A]] {
    def finishTx(result: Interpreter.SQLEither[A], tx: Tx) = {
      result match {
        case Right(_) => tx.commit()
        case Left(_)  => tx.rollback()
      }
      result
    }
  }

  DBs.setupAll()

  println("-------------------------------")

  println(DB.localTx(testApp.run))

  println("-------------------------------")

  println(DB.localTx(failApp.run))

  def debug = program[Query].foldMap(Interpreter.tester)

  println("-------------------------------")

  println(
    debug
      .run(TesterBuffer(
        Vector(true, 1L, 2L, Vector(Account(1, "test1")), Vector(Account(1, "test1")), Option(1), Option(1), "", "")))
      .value
      ._1
      .output
      .mkString("\n"))

  type ProgrammerId = Long
  type SkillId      = Long
  type Name         = String

  def createProgrammer[F[_]](name: Name, skillIds: List[SkillId])(
      implicit S: ScalikeJDBC[F],
      M: Applicative[Free[F, ?]]) = {
    import S._
    for {
      id     <- generateKey(insert.into(Programmer).namedValues(pc.name -> name))
      skills <- list(select.from(Skill.as(s)).where.in(s.id, skillIds))(Skill(s))
      _ <- skills.traverse[Free[F, ?], Boolean](s =>
        execute(insert.into(ProgrammerSkill).namedValues(sc.programmerId -> id, sc.skillId -> s.id)))
    } yield Programmer(id, name, skills)
  }

  def createProgrammer2[F[_]](name: Name, skillIds: List[SkillId])(implicit S: ScalikeJDBC[F], I: Interacts[F]) = {
    import I._
    import S._
    implicit val M = Free.catsFreeMonadForFree[F]

    for {
      name_  <- ask("What is new programer's name?")
      id     <- generateKey(insert.into(Programmer).namedValues(pc.name -> name_))
      skills <- list(select.from(Skill.as(s)).where.in(s.id, skillIds))(Skill(s))
      _ <- skills.traverse[Free[F, ?], Boolean](s =>
        execute(insert.into(ProgrammerSkill).namedValues(sc.programmerId -> id, sc.skillId -> s.id)))
    } yield Programmer(id, name, skills)
  }

  val pc = Programmer.column
  val sc = ProgrammerSkill.column
  val s  = Skill.syntax("s")

}

case class Programmer(id: Long, name: String, skills: Seq[Skill] = Nil)
object Programmer extends SQLSyntaxSupport[Programmer] {
  def apply(s: SyntaxProvider[Programmer])(rs: WrappedResultSet): Programmer = autoConstruct(rs, s, "skills")
}
case class Skill(id: Long, name: String)
object Skill extends SQLSyntaxSupport[Skill] {
  def apply(s: SyntaxProvider[Skill])(rs: WrappedResultSet): Skill = autoConstruct(rs, s)
}
case class ProgrammerSkill(programmerId: Long, skillId: Long)
object ProgrammerSkill extends SQLSyntaxSupport[ProgrammerSkill] {
  def apply(s: SyntaxProvider[ProgrammerSkill])(rs: WrappedResultSet): ProgrammerSkill = autoConstruct(rs, s)
}

sealed trait Interact[A]

case class Ask(prompt: String) extends Interact[String]

case class Tell(msg: String) extends Interact[Unit]

class Interacts[F[_]](implicit I: InjectK[Interact, F]) {
  private def lift[A](v: Interact[A]): Free[F, A] = Free.liftF(I.inj(v))
  def tell(msg: String): Free[F, Unit]            = lift(Tell(msg))
  def ask(prompt: String): Free[F, String]        = lift(Ask(prompt))

  val monad = Free.catsFreeMonadForFree[F]
}
object Interacts {
  implicit def instance[F[_]](implicit I: InjectK[Interact, F]): Interacts[F] = new Interacts[F]
}
