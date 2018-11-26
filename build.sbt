import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.ExecCmd
import scalariform.formatter.preferences._

name := "airlock-sts"

version := "0.1"

scalaVersion := "2.12.6"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "utf-8",
  "-target:jvm-1.8",
  "-feature",
  "-Xlint",
  "-Xfatal-warnings"
)

// Experimental: improved update resolution.
updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

assemblyJarName in assembly := "airlock-sts.jar"

val akkaVersion = "10.1.3"
val keycloakVersion = "4.2.1.Final"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % "2.5.14",
  "ch.megard" %% "akka-http-cors" % "0.3.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-xml" % akkaVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.keycloak" % "keycloak-core" % keycloakVersion,
  "org.keycloak" % "keycloak-adapter-core" % keycloakVersion,
  "org.jboss.logging" % "jboss-logging" % "3.3.2.Final",
  "org.apache.httpcomponents" % "httpclient" % "4.5.6",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test, it",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % Test,
  "com.amazonaws" % "aws-java-sdk-sts" % "1.11.376" % IntegrationTest,
  "org.mariadb.jdbc" % "mariadb-java-client" % "2.3.0")


configs(IntegrationTest)

Defaults.itSettings

parallelExecution in IntegrationTest := false

enablePlugins(JavaAppPackaging)

fork := true

dockerExposedPorts := Seq(12345)
dockerCommands += ExecCmd("ENV", "PROXY_HOST", "0.0.0.0")
dockerBaseImage := "openjdk:8u171-jre-slim-stretch"
dockerAlias := docker.DockerAlias(Some("docker.io"),
  Some("wbaa"),
  "airlock-sts",
  Option(System.getenv("DOCKER_TAG")))

scalariformPreferences := scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(DoubleIndentMethodDeclaration, true)
  .setPreference(NewlineAtEndOfFile, true)
  .setPreference(SingleCasePatternOnNewline, false)

scalastyleFailOnError := true

