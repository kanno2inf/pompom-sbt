name := """pompom-sbt"""

version := "0.1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.jsoup" % "jsoup" % "1.10.1"
)

mainClass in assembly := Some("info.kion2k.pom2sbt.Main")

assemblyOutputPath in assembly := file(s"./${name.value}-${version.value}.jar")
