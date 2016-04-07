package com.github.tarasbilinsky.scalaebean

import com.avaje.ebean._

object EbeanShortcutsNonMacro {

  val sortDesc = " desc"
  val sortAsc = " asc"

  class EbeanTransactionControl(val server:EbeanServer, val transaction:Transaction){
    def this() = this(Ebean.getServer(null),null)
  }

  def transaction[A](b:EbeanTransactionControl=>A):A = transaction(b,Ebean.getServer(null))

  def transaction[A](b:EbeanTransactionControl=>A, server: EbeanServer):A = {
    val tr: Transaction = server.createTransaction()
    val dbTrC = new EbeanTransactionControl(server,tr)
    try {
      val a = b(dbTrC)
      tr.commit()
      tr.end()
      a
    } catch {
      case e: Throwable =>
        tr.rollback(e)
        tr.end()
        throw e
    }
  }

  abstract class StoredProcedureParameter
  case class In( value: Any) extends StoredProcedureParameter
  case class Out( sqlType: Int) extends StoredProcedureParameter

  def executeStoredProcedureMultiOut[T](name: String, params: StoredProcedureParameter*)(
    implicit db: EbeanTransactionControl = new EbeanTransactionControl): Seq[T] ={
    val paramsPlaceholders:String = params.map(_=>"?").mkString(",")
    val cs = db.server.createCallableSql(s"{call $name($paramsPlaceholders)}")
    val paramsWithIndex = params.zipWithIndex
    for((p,i)<-paramsWithIndex){
      p match {
        case in: In => cs.setParameter(i+1,in.value)
        case out: Out => cs.registerOut(i+1,out.sqlType)
      }
    }
    db.server.execute(cs, db.transaction)

    paramsWithIndex.filter(_._1.isInstanceOf[Out]).map{
      case (p:Out,i) => cs.getObject(i+1).asInstanceOf[T]
    }
  }

  def executeStoredProcedureReadOut[T](name: String,  outParam: Out, params: String*)(
    implicit db: EbeanTransactionControl = new EbeanTransactionControl): T =
    executeStoredProcedureMultiOut[T](
      name,
      params.map(p=> In(p)).toList ::: List(outParam): _*)(
        db).head

  def executeStoredProcedure(name: String, params: String*)(
    implicit db: EbeanTransactionControl):Unit =
    executeStoredProcedureMultiOut[AnyRef](name, params.map(p=> In(p)): _*)

  def executeSql(sql: String)(implicit db: EbeanTransactionControl = new EbeanTransactionControl): Int = db.server.execute(db.server.createSqlUpdate(sql),db.transaction)

  def save(m: Model)(implicit db: EbeanTransactionControl = new EbeanTransactionControl): Unit = db.server.save(m,db.transaction)

  def delete(m: Model)(implicit db: EbeanTransactionControl = new EbeanTransactionControl): Unit = db.server.delete(m,db.transaction)


}
