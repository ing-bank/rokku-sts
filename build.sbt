import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.ExecCmd
import scalariform.formatter.preferences._

name := "gargoyle-sts"

version := "0.1"

scalaVersion := "2.12.6"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "utf-8",
  "-target:jvm-1.8",
  "-feature"
)

// Experimental: improved update resolution.
updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

assemblyJarName in assembly := "gargoyle-sts.jar"

scalastyleFailOnError := true

coverageEnabled in(Test, compile) := true

coverageEnabled in(Compile, compile) := false

coverageFailOnMinimum := true

val akkaVersion = "10.1.3"
val keycloakVersion = "4.1.0.Final"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % "2.5.11",
  "ch.megard" %% "akka-http-cors" % "0.3.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-xml" % akkaVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.keycloak" % "keycloak-core" % keycloakVersion,
  "org.keycloak" % "keycloak-adapter-core" % keycloakVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.scalamock" %% "scalamock" % "4.1.0" % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % Test)


assemblyMergeStrategy in assembly := {
  case "application.conf" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

enablePlugins(JavaAppPackaging)

fork := true

dockerExposedPorts := Seq(12345) // should match PROXY_PORT
dockerCommands     += ExecCmd("ENV", "PROXY_HOST", "0.0.0.0")
dockerBaseImage    := "openjdk:8u171-jre-slim-stretch"
dockerAlias        := docker.DockerAlias(Some("docker.io"), Some("kr7ysztof"), "gargoyle-sts", Some(Option(System.getenv("TRAVIS_BRANCH")).getOrElse("latest")))

scalariformPreferences := scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(DanglingCloseParenthesis, Preserve)