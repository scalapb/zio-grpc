ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.14"

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.16.0")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.21.1")

addSbtPlugin("com.thesamet" % "sbt-protoc-gen-project" % "0.1.8")

addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.5.2")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.10.0")
