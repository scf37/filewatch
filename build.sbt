lazy val filewatch = (project in file("."))
    .settings(

    name := "filewatch",
    organization := "me.scf37.filewatch",

    scalaVersion := "2.11.8",

    resolvers += "Scf37" at "https://dl.bintray.com/scf37/maven/",

    libraryDependencies ++= Seq(
        "org.scalatest" % "scalatest_2.11" % "3.0.0" % "test"
    ),

    publish := {},
    releaseTagComment := s"[ci skip]Releasing ${(version in ThisBuild).value}",
    releaseCommitMessage := s"[ci skip]Setting version to ${(version in ThisBuild).value}",
    resourceGenerators in Compile <+= buildProperties,

    bintrayOmitLicense := true,

    bintrayVcsUrl := Some("git@github.com:scf37/filewatch.git")
)