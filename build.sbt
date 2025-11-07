import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.Cmd
import scalariform.formatter.preferences.*

import scala.collection.immutable.Seq

val rokkuStsVersion = scala.sys.env.getOrElse("ROKKU_STS_VERSION", "SNAPSHOT")

name := "rokku-sts"
version := rokkuStsVersion
scalaVersion := "2.13.17"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "utf-8",
  "-target:11",
  "-feature",
  "-Xlint:-byname-implicit",
  "-Xfatal-warnings",
)

// Experimental: improved update resolution.
updateOptions := updateOptions.value.withCachedResolution(true)

assemblyJarName in assembly := "rokku-sts.jar"

val akkaVersion = "2.6.19"
val akkaHttpVersion = "10.2.9"
val keycloakVersion = "21.0.2"
val logbackJson = "0.1.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka"          %% "akka-http"             % akkaHttpVersion,
  "com.typesafe.akka"          %% "akka-stream"           % akkaVersion,
  "ch.megard"                  %% "akka-http-cors"        % "1.1.3",
  "com.typesafe.akka"          %% "akka-http-spray-json"  % akkaHttpVersion,
  "com.typesafe.akka"          %% "akka-http-xml"         % akkaHttpVersion,
  "com.typesafe.scala-logging" %% "scala-logging"         % "3.9.2",
  "ch.qos.logback"             %  "logback-classic"       % "1.4.7",
  "com.typesafe.akka"          %% "akka-slf4j"            % akkaVersion,
  "org.keycloak"               %  "keycloak-core"         % keycloakVersion,
  "org.keycloak"               %  "keycloak-adapter-core" % keycloakVersion,
  "org.keycloak"               %  "keycloak-admin-client" % keycloakVersion,
  "org.jboss.logging"          %  "jboss-logging"         % "3.5.0.Final",
  "org.apache.httpcomponents"  %  "httpclient"            % "4.5.14",
  "ch.qos.logback.contrib"     %  "logback-json-classic"  % logbackJson,
  "ch.qos.logback.contrib"     %  "logback-jackson"       % logbackJson,
  "com.auth0"                  %  "java-jwt"              % "4.3.0",
  "com.bettercloud"            %  "vault-java-driver"     % "5.1.0",
  "redis.clients"              %  "jedis"                 % "4.4.0",
  "org.scalatest"              %% "scalatest"             % "3.2.15"        % "test, it",
  "com.typesafe.akka"          %% "akka-http-testkit"     % akkaHttpVersion % Test,
  "com.typesafe.akka"          %% "akka-stream-testkit"   % akkaVersion     % Test,
  "com.amazonaws"              %  "aws-java-sdk-sts"      % "1.12.471"      % IntegrationTest,
)
dependencyOverrides  ++= Seq(
  "com.fasterxml.jackson.core" %  "jackson-databind"      % "2.15.1",
)

configs(IntegrationTest)
Defaults.itSettings
Global / lintUnusedKeysOnLoad := false

javaOptions in Universal ++= Seq(
  "-Dlogback.configurationFile=/rokku/logback.xml",
)

enablePlugins(JavaAppPackaging)

fork := true

dockerExposedPorts := Seq(12345)

dockerBuildOptions ++= Seq("--platform=linux/amd64")

dockerCommands ++= Seq(
  Cmd("ENV", "PROXY_HOST", "0.0.0.0"),
  Cmd("USER", "root"),
  Cmd("RUN", "apt-get update && apt-get upgrade -y"),
)


dockerBaseImage := "eclipse-temurin:25-jammy"
dockerAlias := docker.DockerAlias(Some("docker.io"), Some("wbaa"), "rokku-sts", Some(rokkuStsVersion))

scalariformPreferences := scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(DoubleIndentMethodDeclaration, true)
  .setPreference(NewlineAtEndOfFile, true)
  .setPreference(SingleCasePatternOnNewline, false)

scalastyleFailOnError := true
scalariformItSettings
scalariformAutoformat := true
