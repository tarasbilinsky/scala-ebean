# scala-ebean
Helpers for using [Ebean ORM](http://ebean-orm.github.io) in Scala

## Usage

```
@Entity 
public class Item extends Model {
    public Long id;
    public String title;
    public int qty;
 }
```

```
import com.intteh.scala.ebean.EbeanImplicits._
import com.intteh.scala.ebean.EbeanShortcuts._
import com.intteh.scala.ebean.EbeanShortcutsNonMacro._

//Dummy placeholder model for query and expr
val m = new Item()

//Instead of
com.avaje.ebean.createQuery(ClassOf[Item])select("title").where().gt("qty",0).like("title","%orange%").findList.asScala
// Use query macro that type checks property names and auto complete works when you write the code
val itemsOrange: Seq[Item] = query(m,m.qty>0 && like(m.title,"%orange%"),m.title).seq

val item5: Item = query(m,m.id==5).one

val items456: Seq[Item] = query(m,in(m.id,4,5,6)).seq

val itemsRaw: Seq[Item] = query(m,raw("exists (select id from item_type where item_id = t0.id)")).seq

val expr: com.avaje.ebean.Expression = expr(m, m.id>0)
val itemsRaw: Seq[Item] = query(m,m.qty>0 && wrap(expr)


//Transaction control
transaction { implicit db =>
    val newRecord = new Item
    ...
    newRecord.save()
    executeSql(s" ... ")
    ...
    val item5: Item = query(m,m.id==5).one
    ...
    val id = executeStoredProcedureReadOut[Long]("execSqlReturnId", Out(java.sql.Types.BIGINT), sql)
    
}

```
