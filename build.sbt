lazy val ebean = (project in file(".")).
  settings(
    organization := "com.github.tarasbilinsky",
    name := "scala-ebean-macros",
    version := "0.0.1",
    scalaVersion := "2.11.8",
    scalacOptions += "-feature",
    libraryDependencies ++= Seq(
      "org.avaje.ebeanorm" % "avaje-ebeanorm" % "6.18.1",
      "org.scala-lang" % "scala-compiler" % "2.11.8"
    ),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },

    crossPaths := false,
    autoScalaLibrary := false,

    publishMavenStyle := true,

    publishArtifact in Test := false,

    pomIncludeRepository := { _ => false },

    pomExtra := {
      <url>https://github.com/tarasbilinsky/scala-ebean</url>
        <licenses>
          <license>
            <name>Apache License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:tarasbilinsky/scala-ebean.git</url>
          <connection>scm:git@github.com:tarasbilinsky/scala-ebean.git</connection>
        </scm>
        <developers>
          <developer>
            <id>Taras</id>
            <name>Taras Bilinsky</name>
            <url>https://github.com/tarasbilinsky</url>
          </developer>
        </developers>
    }
  )