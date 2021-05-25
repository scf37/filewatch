lazy val filewatch = (project in file("."))
    .settings(

    name := "filewatch",
    crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.8", "2.13.0", "3.0.0"),

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

    libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.2.9" % "test"
    ),

    Compile / resourceGenerators += buildProperties,
    publishSettings
)

lazy val publishSettings = Seq(
      organization := "me.scf37",
      publishMavenStyle := true,
      description := "Watch directories for file changes, reliably",
      Compile / doc / sources := Seq.empty,
      scmInfo := Some(
            ScmInfo(
                  url("https://github.com/scf37/filewatch"),
                  "git@github.com:scf37/filewatch.git"
            )
      ),
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
      homepage := Some(url("https://github.com/scf37/filewatch")),
      developers := List(
            Developer("scf37", "Sergey Alaev", "scf370@gmail.com", url("https://github.com/scf37")),
      )
)
