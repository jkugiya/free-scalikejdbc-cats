package scalikejdbc.cats.free

import java.sql.SQLException

import cats._
import cats.data._
import cats.free.Free
import cats.implicits._
import scalikejdbc.cats.free.Query._
import scalikejdbc.{ cats => _, _ }

abstract class Interpreter[M[_]](implicit M: Monad[M]) extends (Query ~> M) {

  protected def exec[A](f: DBSession => A): M[A]

  override def apply[A](c: Query[A]): M[A] = c match {
    case GetVector(sql)     => exec(implicit s => sql.apply[Vector]())
    case GetList(sql)       => exec(implicit s => sql.apply())
    case GetOption(sql)     => exec(implicit s => sql.apply())
    case Fold(sql, init, f) => exec(implicit s => sql.foldLeft(init)(f))
    case Execute(sql)       => exec(implicit s => sql.apply())
    case Update(sql)        => exec(implicit s => sql.apply())
    case GenerateKey(sql)   => exec(implicit s => sql.apply())
  }

  def run[A](q: Free[Query, A]): M[A] = q.foldMap(this)

}

object Interpreter {

  lazy val auto = new Interpreter[Id] {
    protected def exec[A](f: DBSession => A) = f(AutoSession)
  }

  type SQLEither[A] = Either[SQLException, A]
  object SQLEither {
    implicit def TxBoundary[A] = new TxBoundary[SQLEither[A]] {
      def finishTx(result: SQLEither[A], tx: Tx) = {
        result match {
          case Right(_) => tx.commit()
          case Left(_)  => tx.rollback()
        }
        result
      }
    }
  }
  lazy val safe = new Interpreter[SQLEither] {

    protected def exec[A](f: DBSession => A) = Validated.catchOnly[SQLException](f(AutoSession)).toEither
  }

  type TxExecutor[A] = Reader[DBSession, A]
  lazy val transaction = new Interpreter[TxExecutor] {
    protected def exec[A](f: DBSession => A) = Reader.apply(f)
  }

  type SafeExecutor[A] = ReaderT[SQLEither, DBSession, A]
  lazy val safeTransaction = new Interpreter[SafeExecutor] {
    protected def exec[A](f: DBSession => A) = {
      Kleisli { s: DBSession =>
        Validated.catchOnly[SQLException](f(s)).toEither
      }
    }
  }

  case class TesterBuffer(input: Vector[Any], output: Vector[(String, collection.Seq[Any])] = Vector())
  type Tester[A] = State[TesterBuffer, A]
  lazy val tester = new Interpreter[Tester] {
    protected def exec[A](f: DBSession => A) = ???

    override def apply[A](c: Query[A]): Tester[A] = {
      State[TesterBuffer, A] {
        case TesterBuffer(head +: tail, output) =>
          TesterBuffer(tail, output :+ (c.statement -> c.parameters)) -> head.asInstanceOf[A]
      }
    }

  }

}
