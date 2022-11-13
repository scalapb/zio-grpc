ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.12"

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.11.0")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")

addSbtPlugin("com.thesamet" % "sbt-protoc-gen-project" % "0.1.8")

addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.6")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.8.0")
