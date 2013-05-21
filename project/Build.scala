import sbt._
import Keys._

// sbt-release plugin
import sbtrelease.ReleasePlugin._
import sbtrelease._
import ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.ReleaseKeys._

object nisperoCLIBuild extends Build {

  lazy val nisperoCLI = Project(
    id = "nisperoCLI",
    base = file("."),
    settings = Defaults.defaultSettings ++ releaseSettings ++ Seq(

        releaseProcess <<= thisProjectRef apply { ref =>
          Seq[ReleaseStep](
            checkSnapshotDependencies,              // : ReleaseStep
            inquireVersions,                        // : ReleaseStep
            runTest,                                // : ReleaseStep
            setReleaseVersion,                      // : ReleaseStep
            publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
            uploadArtifacts,                        // : ReleaseStep, uploads generated artifacts to s3
            setNextVersion                         // : ReleaseStep
          )
        }
      )
  )

  def recursiveListFiles(file: File): List[File] = {
    val these = file.listFiles.toList
    these.filter(_.isDirectory).flatMap(recursiveListFiles) ++ these
  }

  def renameFile(file: File, s1: String, s2: String) {
    file.renameTo(new File(
      file.getParentFile(), 
      file.getName().replace(s1, s2))
    )
  }

  // sample release step
  val uploadArtifacts = ReleaseStep(action = st => {
    // extract the build state
    val extracted = Project.extract(st)
    // get version
    // WARN: this is the version in build.sbt!!
    val releasedVersion = extracted.get(version in ThisBuild)

    val s3cmdOutput: String = if (releasedVersion.endsWith("-SNAPSHOT")) {

      st.log.info("a snapshot release")
      st.log.info("artifacts will be uploaded to the snapshots repo")

      // recursiveListFiles(new File("artifacts/snapshots.era7.com/")).foreach(
      //   renameFile(_, "nisperocli_2.10", "nisperocli_2.10.0")
      // )

      // Seq(
      //   "mv",
      //   "artifacts/snapshots.era7.com/ohnosequences/nisperocli_2.10",
      //   "artifacts/snapshots.era7.com/ohnosequences/nisperocli_2.10.0"
      // ).!!

      Seq (
            "s3cmd", "sync", "-r", "--no-delete-removed", "--disable-multipart",
            "artifacts/snapshots.era7.com/",
            "s3://snapshots.era7.com/"
          ).!!

    } else {

      st.log.info("a normal release")
      st.log.info("artifacts will be uploaded to the releases repo")

      // recursiveListFiles(new File("artifacts/releases.era7.com/")).foreach(
      //   renameFile(_, "nisperocli_2.10", "nisperocli_2.10.0")
      // )

      // Seq(
      //   "mv",
      //   "artifacts/releases.era7.com/ohnosequences/nisperocli_2.10",
      //   "artifacts/releases.era7.com/ohnosequences/nisperocli_2.10.0"
      // ).!!
      
      Seq (
          "s3cmd", "sync", "-r", "--no-delete-removed", "--disable-multipart",
          "artifacts/releases.era7.com/",
          "s3://releases.era7.com/"
        ).!!
    }

    st.log.info("output from s3cmd: ")
    st.log.info(s3cmdOutput)

    st
  })

}