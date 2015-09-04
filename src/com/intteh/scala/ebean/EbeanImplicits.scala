package com.intteh.scala.ebean

import java.text.SimpleDateFormat

import com.avaje.ebean._
import scala.collection.JavaConverters._
import EbeanShortcutsNonMacro.EbeanTransactionControl

object EbeanImplicits {
  implicit def queryImplicit[T](query: Query[T]):EbeanImplicitQuery[T] = new EbeanImplicitQuery(query)
  implicit def querySqlImplicit(query: SqlQuery):EbeanImplicitSqlQuery = new EbeanImplicitSqlQuery(query)
  implicit def dateToStringImplicit(date: java.util.Date):String = {
    val fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    fmt.format(date)
  }
  //TODO helpers for easy date compare expressions
}


class EbeanImplicitQuery[T](val query: Query[T]){
  def seq()(implicit db: EbeanTransactionControl =  new EbeanTransactionControl()):Seq[T] = db.server.findList(query,db.transaction).asScala
  def one()(implicit db: EbeanTransactionControl =  new EbeanTransactionControl()):Option[T] = Option(db.server.findUnique(query.setMaxRows(1),db.transaction))
}

class EbeanImplicitSqlQuery(val query: SqlQuery){
  def seq()(implicit db: EbeanTransactionControl =  new EbeanTransactionControl()):Seq[SqlRow] = db.server.findList(query,db.transaction).asScala
  def one()(implicit db: EbeanTransactionControl =  new EbeanTransactionControl()):Option[SqlRow] = Option(db.server.findUnique(query.setMaxRows(1),db.transaction))
}
