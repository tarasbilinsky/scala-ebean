lazy val root = (project in file(".")).
  settings(
    name := "scala-ebean-macros",
    version := "0.0.1",
    scalaVersion := "2.11.8",
    scalacOptions += "-feature",
    libraryDependencies ++= Seq(
      "org.avaje.ebeanorm" % "avaje-ebeanorm" % "6.18.1",
      "org.scala-lang" % "scala-compiler" % "2.11.8"
    )
  )