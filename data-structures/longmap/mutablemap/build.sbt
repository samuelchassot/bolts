import Dependencies._

ThisBuild / scalaVersion := "3.3.0"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "ch.epfl"
ThisBuild / organizationName := "epfl"

lazy val root = (project in file("."))
  .enablePlugins(StainlessPlugin)
  .settings(
    name := "MutableMap",
    stainlessEnabled := false
  )
