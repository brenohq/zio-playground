ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "zio-playground"
  )

libraryDependencies += "dev.zio" %% "zio" % "1.0.9"
libraryDependencies += "dev.zio" %% "zio-streams" % "1.0.9"
