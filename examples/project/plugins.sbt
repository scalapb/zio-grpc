resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.27")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.0-M6"

libraryDependencies += "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.0.0+34-628a37dc-SNAPSHOT"
