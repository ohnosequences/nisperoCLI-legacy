import sbtrelease._
import ReleaseStateTransformations._

import nisperoCLIBuild._

name := "nisperoCLI"

organization := "ohnosequences"

version := "0.2.7"

scalaVersion := "2.10.0"

publishMavenStyle := true


publishTo <<= version { (v: String) =>
  if (v.trim.endsWith("SNAPSHOT"))
    Some(Resolver.file("local-snapshots", file("artifacts/snapshots.era7.com")))
  else
    Some(Resolver.file("local-releases", file("artifacts/releases.era7.com")))
}


resolvers ++= Seq (
                    "Typesafe Releases"   at "http://repo.typesafe.com/typesafe/releases",
                    "Sonatype Releases"   at "https://oss.sonatype.org/content/repositories/releases",
                    "Sonatype Snapshots"  at "https://oss.sonatype.org/content/repositories/snapshots",
                    "Era7 Releases"       at "http://releases.era7.com.s3.amazonaws.com",
                    "Era7 Snapshots"      at "http://snapshots.era7.com.s3.amazonaws.com"
                  )

libraryDependencies += "ohnosequences" % "scriptexecutor_2.10" % "0.2.7"

libraryDependencies += "ohnosequences" % "nisperobase_2.10" % "0.2.7"

libraryDependencies += "com.novocode" % "junit-interface" % "0.10-M1" % "test"

libraryDependencies += "com.github.scopt" % "scopt_2.10" % "2.1.0"

libraryDependencies += "org.scala-sbt" % "launcher-interface" % "0.12.3" % "provided"

resolvers <+= sbtResolver


