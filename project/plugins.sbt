addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.27")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.6"

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.0")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.0")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
