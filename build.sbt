lazy val filewatch = (project in file("."))
    .settings(

    name := "filewatch",
    organization := "me.scf37.filewatch",
    crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.8", "2.13.0"),
    releaseCrossBuild := true,

    scalaVersion := "2.11.8",

    scalacOptions := Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-deprecation",
        "-language:implicitConversions",
        "-language:higherKinds",
        "-unchecked",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Xlint"
    ),

    resolvers += "Scf37" at "https://dl.bintray.com/scf37/maven/",

    libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.0.8" % "test"
    ),

//    publish := {},
    releaseTagComment := s"[ci skip]Releasing ${(version in ThisBuild).value}",
    releaseCommitMessage := s"[ci skip]Setting version to ${(version in ThisBuild).value}",
    resourceGenerators in Compile <+= buildProperties,

    bintrayOmitLicense := true,

    bintrayVcsUrl := Some("git@github.com:scf37/filewatch.git")
)