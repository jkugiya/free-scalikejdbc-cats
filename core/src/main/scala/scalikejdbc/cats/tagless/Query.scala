package scalikejdbc.cats.tagless

import java.sql.SQLException

import cats._
import cats.data._
import cats.implicits._
import scalikejdbc.{ DBSession, _ }

sealed abstract class ScalikeJDBC[F[_]: Monad] {
  protected def exec[A](f: DBSession => A): F[A]

  def vector[A](sql: SQLBuilder[_])(f: WrappedResultSet => A): F[Vector[A]] =
    exec(implicit s => withSQL(sql).map(f).toCollection.apply[Vector]())
  def single[A](sql: SQL[A, HasExtractor]): F[Option[A]]                    = exec(implicit s => sql.single().apply())
  def single[A](sql: SQLBuilder[_])(f: WrappedResultSet => A): F[Option[A]] = single(withSQL(sql).map(f))
  def generateKey(sql: SQLBuilder[UpdateOperation]): F[Long] =
    exec(implicit s => withSQL(sql).updateAndReturnGeneratedKey().apply())

}

abstract class Interpreter {
  type F[A]

  def vector[A](sql: SQLBuilder[_])(f: WrappedResultSet => A)(implicit F: ScalikeJDBC[F]): F[Vector[A]] =
    F.vector(sql)(f)
  def single[A](sql: SQL[A, HasExtractor])(implicit F: ScalikeJDBC[F]): F[Option[A]] = F.single(sql)
  def single[A](sql: SQLBuilder[_])(f: WrappedResultSet => A)(implicit F: ScalikeJDBC[F]): F[Option[A]] =
    F.single(sql)(f)
  def generateKey(sql: SQLBuilder[UpdateOperation])(implicit F: ScalikeJDBC[F]): F[Long] = F.generateKey(sql)

}

object Interpreter {

  private def eitherTxBoundary[A] = new TxBoundary[Either[SQLException, A]] {
    def finishTx(result: Either[SQLException, A], tx: Tx) = {
      result match {
        case Right(_) => tx.commit()
        case Left(_)  => tx.rollback()
      }
      result
    }
  }

  object auto extends Interpreter {
    type F[A] = Id[A]
    implicit val autoInterpreter = new ScalikeJDBC[F] {
      protected def exec[A](f: DBSession => A): F[A] = f(AutoSession)
    }
  }

  object safe extends Interpreter {
    type F[A] = Either[SQLException, A]
    implicit def TxBoundary[A]: TxBoundary[F[A]] = eitherTxBoundary
    implicit val safeInterpreter = new ScalikeJDBC[F] {
      protected def exec[A](f: DBSession => A) = Validated.catchOnly[SQLException](f(AutoSession)).toEither
    }
  }

  object transaction extends Interpreter {
    type F[A] = Reader[DBSession, A]
    implicit val txInterpreter = new ScalikeJDBC[F] {
      protected def exec[A](f: DBSession => A) = Reader(f)
    }
  }

  object safeTransaction extends Interpreter {
    type SQLEither[A] = Either[SQLException, A]
    type F[A]         = ReaderT[SQLEither, DBSession, A]
    implicit def TxBoundary[A]: TxBoundary[SQLEither[A]] = eitherTxBoundary
    implicit val txInterpreter = new ScalikeJDBC[F] {
      protected def exec[A](f: DBSession => A) = {
        Kleisli { s: DBSession =>
          Validated.catchOnly[SQLException](f(s)).toEither
        }
      }
    }
  }

}
