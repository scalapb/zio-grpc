addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.1.0-RC2")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.21"

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.2")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")

addSbtPlugin("com.thesamet" % "sbt-protoc-gen-project" % "0.2.0")

addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.9.2")
