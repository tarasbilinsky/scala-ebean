package test

import net.oltiv.scalaebean.Shortcuts._
class Debug {
  val m = new ParentModel
  val cls = classOf[ParentModel]
  val x = query(m,m.name==0,m.children,m.child,m,m.name)

}
