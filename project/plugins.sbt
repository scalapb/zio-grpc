ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.30")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.2"

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.2")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
