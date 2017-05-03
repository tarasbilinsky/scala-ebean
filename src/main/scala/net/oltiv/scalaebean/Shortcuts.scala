package net.oltiv.scalaebean

import java.text.SimpleDateFormat

import com.avaje.ebean._

import scala.language.experimental.macros
import scala.reflect.ClassTag
import scala.reflect.macros._

class ModelField[+T <: Model](val name: String)(implicit tag: ClassTag[T])
object ModelField{
  def apply[T <: Model](cls: Class[T], p: String)(implicit tag: ClassTag[T]):Seq[ModelField[T]] = p.split(",").map(new ModelField[T](_))
  def apply[T <: Model](m: T, select: Any*):Seq[ModelField[T]] = macro Shortcuts.modelFieldsMacroImpl[T]
}


object Shortcuts {

  private val EbeanPackage = "com.avaje.ebean"
  private val EbeanModelClass = EbeanPackage+".Model"

  // not macro but has be be here as it result in ambiguous reference compiler error when placed into other object
  def query(sql: String): SqlQuery = Ebean.createSqlQuery(sql)


  def query[T <: Model](m: T, q: Boolean, select: Any*): Query[T] = macro queryMacroImpl[T]

  def query[T <: Model](mClass: Class[T], m: T, q: Boolean, select: Any*): Query[T] = macro queryWClassMacroImpl[T]

  def query[T <: Model](m: T, q: Boolean): Query[T] = macro queryWoSelectMacroImpl[T]

  def query[T <: Model](mClass: Class[T], m: T, q: Boolean): Query[T] = macro queryWClassWoSelectMacroImpl[T]

  def query[T <: Model](m: T): Query[T] = macro queryAllMacroImpl[T]

  def expr(m: Model, q: Boolean): Expression = macro exprMacroImpl

  def props(m: Model, select: Any*): String = macro propsMacroImpl

  def in(m:Any,cond: Any*):Boolean = true //for macro only, never actually called
  def like(m:Any, template: String):Boolean = true //never actually called
  def raw(raw: String):Boolean = true //never actually called
  def wrap(m: Expression):Boolean = true//never actually called


  private abstract class CommonMacroCode {
    val cw: blackbox.Context
    import cw.universe._
    val modelName: String
    val modelTypeSymbol: cw.Type
    val autoAll: Boolean = true


    private val modelPropRE = s"\\Q$modelName.\\E([a-zA-Z0-9_\\.]*)".r
    private val modelPropWithImplicitRE = s"\\Qnet.oltiv.scalaebean.Shortcuts.\\E[a-zA-Z0-9_]*\\Q($modelName.\\E([a-zA-Z0-9_\\.]*)\\Q)\\E".r

    private val modelNameWDot = modelName + "."
    private val lengthOfModelNameWDot = modelNameWDot.length
    private val e = q"com.avaje.ebean.Expr"

    protected object NodeType extends Enumeration {
      type N = Value
      val SubTree, Property, Val, ValMany = Value
    }



    def exception(msg: String): Nothing = cw.abort(cw.enclosingPosition, "Shortcuts macro error. " + msg)

    def isModelProp(s: Seq[String], typeSymbol: cw.Type):Boolean ={
      if(s.isEmpty) false else {
        val (head,tail) = (s.head,s.tail)
        val nextClsOption: Option[cw.Symbol] = typeSymbol.members.find(_.name.toString==head)
        val nextCls: cw.Type = if(nextClsOption.isDefined) nextClsOption.get.typeSignature else exception(s"$head field not found in ${typeSymbol.toString}")
        if(nextCls.baseClasses.exists(_.fullName==EbeanModelClass) || nextCls.typeArgs.exists(ta=>ta.baseClasses.exists(_.fullName==EbeanModelClass))){
          if(tail.isEmpty) true else isModelProp(tail,nextCls)
        } else {
          if(tail.isEmpty) false else exception(s"Incorrect property $s")
        }
      }
    }

    def processSelect(t: cw.Expr[Any]) = {
      val s = t.tree.toString
      if(s==modelName) "*" else
      if (s.startsWith(modelNameWDot)){
        val r = s.substring(lengthOfModelNameWDot)
        val r2 = if(r.startsWith("`") && r.endsWith("`")) r.substring(1,r.length-1) else r
        val r3 = if(autoAll && isModelProp(r2.split("\\."),modelTypeSymbol)) r2 + ".*" else r2
        r3
      }
      else exception(s"Select list must start with model name but got $s instead")
    }

    def makeQry(t: Tree, n: NodeType.N = NodeType.SubTree): Tree = {
      n match {
        case NodeType.SubTree =>
          val s = t.symbol
          s match {
            case sm if sm.isMethod =>
              case class M(em: Tree, p1: NodeType.N, p2: NodeType.N, fnParamPos: Boolean = false)
              val m = sm.asMethod

              val em = m.name.toString match {
                case "$greater" => new M(q"$e.gt", NodeType.Property, NodeType.Val)
                case "$eq$eq" => new M(q"$e.eq", NodeType.Property, NodeType.Val)
                case "$bang$eq" => new M(q"$e.ne", NodeType.Property, NodeType.Val)
                case "$less" => new M(q"$e.lt", NodeType.Property, NodeType.Val)
                case "$less$eq" => new M(q"$e.le", NodeType.Property, NodeType.Val)
                case "$greater$eq" => new M(q"$e.ge", NodeType.Property, NodeType.Val)
                case "$bar$bar" => new M(q"$e.or", NodeType.SubTree, NodeType.SubTree)
                case "$amp$amp" => new M(q"$e.and", NodeType.SubTree, NodeType.SubTree)
                case "unary_$bang" => new M(q"$e.not", NodeType.SubTree, null)
                case "in" => new M(q"$e.in", NodeType.Property, NodeType.ValMany, true)
                case "like" => new M(q"$e.like", NodeType.Property, NodeType.Val, true)
                case "raw" => new M(q"$e.raw", NodeType.Val, null, true)
                case "wrap" => new M(null, NodeType.Val, null, true)
                case "Boolean2boolean" => new M(null, NodeType.SubTree, null, true)
                case emt@_ => exception(s"$emt method not handled")
              }

              val c = t.children
              val opParamPosition = !em.fnParamPos

              Option(em.p2) match {
                case Some(p2m) =>
                  val p1 = makeQry(if (opParamPosition) c.head.children.head else c.tail.head, em.p1)
                  if (p2m != NodeType.ValMany) {
                    val p2 = makeQry(if (opParamPosition) c.tail.head else c.tail.tail.head, p2m)
                    q"${em.em}($p1,$p2)"
                  } else {
                    val coll = c.tail.tail.map(e => makeQry(e, NodeType.Val)).map(e => Apply(Select(Ident(TermName("l")), TermName("add")), List(e)))
                    val adds = Block(coll, Ident(TermName("l")))

                    q"${em.em}($p1, {val l = new java.util.ArrayList[Object];$adds})"
                  }

                case None =>
                  val p1 = makeQry(if (opParamPosition) c.head else c.tail.head, em.p1)
                  Option(em.em) match {
                    case Some(emm) => q"${em.em}($p1)"
                    case None => p1
                  }

              }

            case _ =>
              // try parse as boolean property
              t.toString match {
                case modelPropRE(p) => q"$e.eq($p,true)"
                case r@_ => exception(s"Unexpected, not method in ${t.toString}; Tried parse as property: Invalid property $r, expected $modelName.somepropname.")
              }
          }

        case NodeType.Property =>
          t.toString match {
            case modelPropRE(p) => q"$p"
            case modelPropWithImplicitRE(p) => q"$p"
            case r@_ => exception(s"Invalid property $r, expected $modelName.somepropname.")
          }

        case NodeType.Val => t
      }
    }

    def makeSelectAndFetch(selectAsStrings: Seq[String]):(Tree,Tree) = {
      def makeFieldsList(l: Seq[String]) = l.mkString(",")

      val (fetchList, selectOnlyNoFetches) = selectAsStrings.partition(s => s.contains('.'))

      val fetchList2 = fetchList.map(s => {
        val p = s.lastIndexOf('.')
        (s.substring(0, p), s.substring(p + 1))
      })

      val fetchList3: Map[String, Seq[(String, String)]] = fetchList2.groupBy(_._1)

      val fetchList4 = fetchList3.map { case (k, v) => (k, v.map(_._2)) }

      val fetchList5 = fetchList4.map { case (k, v) => (k, makeFieldsList(v)) }

      val fetchListFinal = fetchList5.map { case (k, v) => (k, v) }(collection.breakOut)


      val sl = if (selectOnlyNoFetches.isEmpty) "*" else makeFieldsList(selectOnlyNoFetches)
      val sq = q"$sl"

      def processFetch(l: Seq[(String, String)], a: Tree): Tree = {
        if (l.isEmpty) a
        else {
          val h = l.head
          val t = l.tail
          Apply(Select(processFetch(t, a), TermName("fetch")), List(Literal(Constant(h._1)), Literal(Constant(h._2))))
        }
      }

      val fetch = processFetch(fetchListFinal, Ident(TermName("q")))

      (sq,fetch)
    }

    def makeSelectAll:Tree={
      val sl =  "*"
      val sq = q"$sl"
      sq
    }
  }

  def propsMacroImpl(c: blackbox.Context)(m: c.Expr[Model], select: c.Expr[Any]*): c.Expr[String] = {
    val w = new {
      val cw: c.type = c
      val modelName = m.tree.toString
      val modelTypeSymbol: c.Type = m.actualType
      override val autoAll: Boolean = false
    } with CommonMacroCode

    val selectAsStrings = select.map(w.processSelect)
    val res = selectAsStrings.mkString(",")

    import c.universe._
    c.Expr[String](
      q"$res"
    )
  }


  def exprMacroImpl(c: blackbox.Context)(m: c.Expr[Model], q: c.Expr[Boolean]): c.Expr[com.avaje.ebean.Expression] = {
    val w = new {
      val cw: c.type = c
      val modelName = m.tree.toString
      val modelTypeSymbol: c.Type = m.actualType
    } with CommonMacroCode

    val qryResTree = w.makeQry(q.tree)
    c.Expr[com.avaje.ebean.Expression](
      qryResTree
    )
  }

  def queryAllMacroImpl[T <: Model : c.WeakTypeTag](c: blackbox.Context)(m: c.Expr[T]): c.Expr[Query[T]] = {
    val w = new {
      val cw: c.type = c
      val modelName = m.tree.toString
      val modelTypeSymbol: c.Type = m.actualType
    } with CommonMacroCode
    val sq = w.makeSelectAll
    val modelType = c.weakTypeOf[T]
    import c.universe._
    c.Expr[com.avaje.ebean.Query[T]](
      q"com.avaje.ebean.Ebean.createQuery(classOf[$modelType]).select($sq)"
    )
  }

  def modelFieldsMacroImpl[T<:Model : c.WeakTypeTag](c: blackbox.Context)(m: c.Expr[T], select: c.Expr[Any]*): c.Expr[Seq[ModelField[T]]] = {
    val w = new {
      val cw: c.type = c
      val modelName = m.tree.toString
      val modelTypeSymbol: c.Type = m.actualType
      override val autoAll: Boolean = false
    } with CommonMacroCode

    val selectAsStrings = select.map(w.processSelect)
    val res = selectAsStrings.mkString(",")
    val modelType = c.weakTypeOf[T]

    import c.universe._
    c.Expr[Seq[ModelField[T]]](
      q"ModelField.apply(classOf[$modelType],$res)"
    )
  }

  def queryWClassWoSelectMacroImpl[T <: Model : c.WeakTypeTag](c: blackbox.Context)(mClass: c.Expr[Class[T]], m: c.Expr[T], q: c.Expr[Boolean]): c.Expr[Query[T]] = {
    val w = new {
      val cw: c.type = c
      val modelName = m.tree.toString
      val modelTypeSymbol: c.Type = m.actualType
    } with CommonMacroCode

    val qryResTree = w.makeQry(q.tree)
    val sq = w.makeSelectAll
    import c.universe._
    c.Expr[com.avaje.ebean.Query[T]](
      q"com.avaje.ebean.Ebean.createQuery($mClass).select($sq).where($qryResTree)"
    )
  }

  def queryWoSelectMacroImpl[T <: Model : c.WeakTypeTag](c: blackbox.Context)(m: c.Expr[T], q: c.Expr[Boolean]): c.Expr[Query[T]] = {
    val w = new {
      val cw: c.type = c
      val modelName = m.tree.toString
      val modelTypeSymbol: c.Type = m.actualType
    } with CommonMacroCode

    val qryResTree = w.makeQry(q.tree)

    val sq = w.makeSelectAll

    val modelType = c.weakTypeOf[T]
    import c.universe._
    c.Expr[com.avaje.ebean.Query[T]](
      q"com.avaje.ebean.Ebean.createQuery(classOf[$modelType]).select($sq).where($qryResTree)"
    )
  }

  def queryMacroImpl[T <: Model : c.WeakTypeTag](c: blackbox.Context)(m: c.Expr[T], q: c.Expr[Boolean], select: c.Expr[Any]*): c.Expr[Query[T]] = {

    val w = new {
      val cw: c.type = c
      val modelName = m.tree.toString
      val modelTypeSymbol: c.Type = m.actualType
    } with CommonMacroCode


    val qryResTree = w.makeQry(q.tree)

    val selectAsStrings = select.map(w.processSelect)
    val (sq,fetch) = w.makeSelectAndFetch(selectAsStrings)

    val modelType = c.weakTypeOf[T]
    import c.universe._
    c.Expr[com.avaje.ebean.Query[T]](
      q"{val q=com.avaje.ebean.Ebean.createQuery(classOf[$modelType]).select($sq);val q2=$fetch;q2.where($qryResTree)}"
    )
  }

  def queryWClassMacroImpl[T <: Model : c.WeakTypeTag](c: blackbox.Context)(mClass: c.Expr[Class[T]], m: c.Expr[T], q: c.Expr[Boolean], select: c.Expr[Any]*): c.Expr[Query[T]] = {

    val w = new {
      val cw: c.type = c
      val modelName = m.tree.toString
      val modelTypeSymbol: c.Type = m.actualType
    } with CommonMacroCode

    val qryResTree = w.makeQry(q.tree)

    val selectAsStrings = select.map(w.processSelect)
    val (sq,fetch) = w.makeSelectAndFetch(selectAsStrings)

    import c.universe._
    c.Expr[com.avaje.ebean.Query[T]](
      q"{val q=com.avaje.ebean.Ebean.createQuery($mClass).select($sq);val q2=$fetch;q2.where($qryResTree)}"
    )
  }

  /*
  NON Macro Shortcuts
   */
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


  /*
  IMPLICITS
   */

  import scala.collection.JavaConverters._
  import scala.language.implicitConversions


  implicit def queryImplicit[T](query: Query[T]):EbeanImplicitQuery[T] = new EbeanImplicitQuery(query)
  implicit def querySqlImplicit(query: SqlQuery):EbeanImplicitSqlQuery = new EbeanImplicitSqlQuery(query)
  implicit def dateToStringImplicit(date: java.util.Date):String = {
    val fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    fmt.format(date)
  }
  //TODO helpers for easy date compare expressions


  def query[T,T1](mClass: Class[T1], xClass: Class[T]): Query[T] = Ebean.createQuery(mClass).asInstanceOf[Query[T]]

  class EbeanImplicitQuery[T](val query: Query[T]){
    def seq()(implicit db: EbeanTransactionControl =  new EbeanTransactionControl()):Seq[T] = db.server.findList(query,db.transaction).asScala
    def one()(implicit db: EbeanTransactionControl =  new EbeanTransactionControl()):Option[T] = Option(db.server.findUnique(query.setMaxRows(1),db.transaction))
  }

  class EbeanImplicitSqlQuery(val query: SqlQuery){
    def seq()(implicit db: EbeanTransactionControl =  new EbeanTransactionControl()):Seq[SqlRow] = db.server.findList(query,db.transaction).asScala
    def one()(implicit db: EbeanTransactionControl =  new EbeanTransactionControl()):Option[SqlRow] = Option(db.server.findUnique(query.setMaxRows(1),db.transaction))
  }


}
