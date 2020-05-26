resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.31")

val zioGrpcVersion = "0.2.0+58-0f13162e+20200525-2043-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.2.0+60-4d8a05b8-SNAPSHOT",
  "com.thesamet.scalapb" %% "compilerplugin" % "0.10.3"
)

// For Scala.js:
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.1.0")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.18.0")
