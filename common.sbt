import sbt._
import Keys._

// Variables:
val scioVersion = "0.1.10"
val sl4jVersion = "1.7.13"
val specsCoreVersion = "3.7.2"
val spotifyDataSchemasVersion = "1.0-SNAPSHOT"

// Scala/Java flags:
scalaVersion := "2.11.8"
scalacOptions ++= Seq("-target:jvm-1.7", "-deprecation", "-feature", "-unchecked", "-Yrangepos")
javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

lazy val commonSettings = Defaults.coreDefaultSettings ++ packAutoSettings ++ Seq(
  name := "scio-wiki",
  organization          := "com.spotify",
  // Use semantic versioning http://semver.org/
  version               := "0.1.0-SNAPSHOT",
  scalaVersion          := "2.11.8",
  scalacOptions         ++= Seq("-target:jvm-1.8", "-deprecation", "-feature", "-unchecked"),
  javacOptions          ++= Seq("-source", "1.8", "-target", "1.8"),
  packJarNameConvention := "original"
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val root: Project = Project(
  "scio-wiki-pipeline",
  file("scio-wiki-pipeline"),
  settings = commonSettings ++ noPublishSettings ++ Seq(
    description := "Wikipedia PageRank Scio",
    libraryDependencies ++= Seq(
      "com.spotify" %% "scio-core" % scioVersion,
      "org.slf4j" % "slf4j-simple" % sl4jVersion,
      "com.spotify" %% "scio-test" % scioVersion % "test",
      "org.specs2" %% "specs2-core" % specsCoreVersion % "test"
    ),
    // Required for typed BigQuery macros:
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    packageOptions in (Compile, packageBin) +=
      Package.ManifestAttributes( "Class-Path" ->
        (((managedClasspath in Runtime).value.files
          .map(f => f.getName)
          .filter(_.endsWith(".jar"))
          .mkString(" ")) + " ")
      )
  )
)

// Run Scala style check as part of (before) tests:
lazy val testScalastyle = taskKey[Unit]("testScalastyle")
testScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Test).toTask("").value
(test in Test) <<= (test in Test) dependsOn testScalastyle

// Repositories and dependencies
resolvers ++= Seq(
  "Concurrent Maven Repo" at "http://conjars.org/repo",
  "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
)

// Enable scoverage:
coverageEnabled in Test := true
jacoco.settings
parallelExecution in jacoco.Config := false

// In case avro/proto is used:
compileOrder := CompileOrder.JavaThenScala
