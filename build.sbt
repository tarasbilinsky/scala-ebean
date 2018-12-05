lazy val ebean = (project in file(".")).
  settings(
    organization := "net.oltiv",
    name := "scala-ebean-macros",
    version := "0.3.101",
    scalaVersion := "2.12.8",
    scalacOptions += "-feature",
    libraryDependencies ++= Seq(
      "io.ebean" % "ebean" % "latest.integration",
      //"io.ebean" % "ebean-agent" % "11.10.1",
      "org.scala-lang" % "scala-compiler" % "2.12.8"
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
      <url>https://github.com/oltiv/scala-ebean</url>
        <licenses>
          <license>
            <name>Apache License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:oltiv/scala-ebean.git</url>
          <connection>scm:git@github.com:oltiv/scala-ebean.git</connection>
        </scm>
        <developers>
          <developer>
            <id>oltiv</id>
            <name>oltiv</name>
            <url>https://github.com/oltiv</url>
          </developer>
        </developers>
    }
  )