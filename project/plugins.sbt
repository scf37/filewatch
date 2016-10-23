addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")

resolvers += Resolver.url("plugins", url("https://dl.bintray.com/scf37/sbt-plugins"))(Resolver.ivyStylePatterns)
addSbtPlugin("me.scf37.buildprops" % "sbt-build-properties" % "1.0.2")

//lazy val root = (project in file("."))
//  .dependsOn( shPlugin )
////    .settings(
////      addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0"),
////      addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0"),
////      addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5"))
//lazy val shPlugin = uri("file:///home/asm/mywork/sbt-build-properties")