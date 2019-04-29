import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.ExecCmd
import scalariform.formatter.preferences._

val rokkuStsVersion = scala.sys.env.getOrElse("ROKKU_STS_VERSION", "SNAPSHOT")

name := "rokku-sts"
version := rokkuStsVersion
scalaVersion := "2.12.8"

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

assemblyJarName in assembly := "rokku-sts.jar"

val akkaVersion = "2.5.19"
val akkaHttpVersion = "10.1.5"
val keycloakVersion = "4.7.0.Final"
val logbackJson = "0.1.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "ch.megard" %% "akka-http-cors" % "0.3.1",
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.keycloak" % "keycloak-core" % keycloakVersion,
  "org.keycloak" % "keycloak-adapter-core" % keycloakVersion,
  "org.jboss.logging" % "jboss-logging" % "3.3.2.Final",
  "org.apache.httpcomponents" % "httpclient" % "4.5.6",
  "org.mariadb.jdbc" % "mariadb-java-client" % "2.3.0",
  "ch.qos.logback.contrib" % "logback-json-classic" % logbackJson,
  "ch.qos.logback.contrib" % "logback-jackson" % logbackJson,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.8",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test, it",
  "com.auth0" % "java-jwt" % "3.8.0",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.19" % Test,
  "com.amazonaws" % "aws-java-sdk-sts" % "1.11.467" % IntegrationTest)


configs(IntegrationTest)

Defaults.itSettings

parallelExecution in IntegrationTest := false

javaOptions in Universal ++= Seq(
  "-Dlogback.configurationFile=/rokku/logback.xml"
)

enablePlugins(JavaAppPackaging)

fork := true

dockerExposedPorts := Seq(12345)
dockerCommands += ExecCmd("ENV", "PROXY_HOST", "0.0.0.0")
dockerBaseImage := "openjdk:8u171-jre-slim-stretch"
dockerAlias := docker.DockerAlias(Some("docker.io"), Some("wbaa"), "rokku-sts", Some(rokkuStsVersion))

scalastyleFailOnError := true

scalafmtOnCompile := true
