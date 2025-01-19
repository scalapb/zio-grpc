import Settings.stdSettings
import org.scalajs.linker.interface.ModuleInitializer

val Scala3 = "3.3.4"

val Scala213 = "2.13.16"

val Scala212 = "2.12.20"

val ScalaVersions = Seq(Scala212, Scala213, Scala3)

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / versionScheme := Some("early-semver")

publish / skip := true

sonatypeProfileName := "com.thesamet"

inThisBuild(
  List(
    organization := "com.thesamet.scalapb.zio-grpc",
    homepage     := Some(url("https://github.com/scalapb/zio-grpc")),
    licenses     := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers   := List(
      Developer(
        "thesamet",
        "Nadav Samet",
        "thesamet@gmail.com",
        url("https://www.thesamet.com")
      )
    )
  )
)

lazy val core = projectMatrix
  .in(file("core"))
  .defaultAxes()
  .settings(stdSettings)
  .settings(
    name := "zio-grpc-core",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio"          % Version.zio,
      "dev.zio" %%% "zio-streams"  % Version.zio,
      "dev.zio" %%% "zio-test"     % Version.zio % "test",
      "dev.zio" %%% "zio-test-sbt" % Version.zio % "test"
    )
  )
  .jvmPlatform(
    ScalaVersions,
    Seq(
      libraryDependencies ++= Seq(
        "io.grpc" % "grpc-services" % Version.grpc
      )
    )
  )
  .customRow(
    true,
    ScalaVersions,
    Seq(VirtualAxis.js),
    _.enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
      .settings(
        libraryDependencies ++= Seq(
          "com.thesamet.scalapb.grpcweb" %%% "scalapb-grpcweb" % "0.7.0",
          "io.github.cquiroz"            %%% "scala-java-time" % "2.6.0" % "test"
        ),
        Compile / npmDependencies += "grpc-web" -> "1.4.2"
      )
  )

lazy val codeGen = projectMatrix
  .in(file("code-gen"))
  .defaultAxes()
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings)
  .settings(
    buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "scalapb.zio_grpc",
    name             := "zio-grpc-codegen",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
    )
  )
  .jvmPlatform(scalaVersions = ScalaVersions)

lazy val codeGenJVM212 = codeGen.jvm(Scala212)

lazy val protocGenZio = protocGenProject("protoc-gen-zio", codeGenJVM212)
  .settings(
    Compile / mainClass              := Some("scalapb.zio_grpc.ZioCodeGenerator"),
    scalaVersion                     := Scala212,
    assembly / assemblyMergeStrategy := {
      case PathList("scala", "annotation", "nowarn.class" | "nowarn$.class") =>
        MergeStrategy.first
      case x                                                                 =>
        (assembly / assemblyMergeStrategy).value.apply(x)
    }
  )

lazy val e2eProtos =
  projectMatrix
    .in(file("e2e") / "protos")
    .dependsOn(core)
    .defaultAxes()
    .enablePlugins(LocalCodeGenPlugin)
    .jvmPlatform(
      ScalaVersions,
      settings = Seq(
        libraryDependencies ++= Seq(
          "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
        )
      )
    )
    .jsPlatform(
      ScalaVersions,
      settings = Seq(
        libraryDependencies ++= Seq(
          "com.thesamet.scalapb.grpcweb" %%% "scalapb-grpcweb" % "0.7.0",
          "io.github.cquiroz"            %%% "scala-java-time" % "2.6.0"
        )
      )
    )
    .settings(stdSettings)
    .settings(
      Defaults.itSettings,
      crossScalaVersions   := Seq(Scala212, Scala213),
      publish / skip       := true,
      Compile / PB.targets := Seq(
        scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
        genModule(
          "scalapb.zio_grpc.ZioCodeGenerator$"
        )                        -> (Compile / sourceManaged).value
      ),
      libraryDependencies ++= Seq(
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
      ),
      codeGenClasspath     := (codeGenJVM212 / Compile / fullClasspath).value
    )

lazy val e2e =
  projectMatrix
    .in(file("e2e"))
    .dependsOn(core, e2eProtos)
    .defaultAxes()
    .jvmPlatform(
      ScalaVersions,
      settings = Seq(
        libraryDependencies ++= Seq(
          "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
          "io.grpc"               % "grpc-netty"           % Version.grpc
        )
      )
    )
    .customRow(
      true,
      ScalaVersions,
      Seq(VirtualAxis.js),
      _.enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
        .settings(
          scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
          Compile / npmDependencies += "grpc-web" -> "1.4.2",
          scalaJSUseMainModuleInitializer         := true
        )
    )
    .configs(IntegrationTest)
    .settings(stdSettings)
    .settings(
      Defaults.itSettings,
      publish / skip       := true,
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio-test"     % Version.zio % "test,it",
        "dev.zio" %%% "zio-test-sbt" % Version.zio % "test,it"
      ),
      Compile / run / fork := true,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )

lazy val e2eWeb =
  projectMatrix
    .in(file("e2e-web"))
    .dependsOn(e2eProtos, e2e % "compile->test")
    .defaultAxes()
    .enablePlugins(BuildInfoPlugin)
    .jvmPlatform(
      scalaVersions = ScalaVersions,
      settings = Seq(
        libraryDependencies ++= Seq(
          "com.microsoft.playwright" % "playwright"   % "1.45.0"    % Test,
          "dev.zio"                %%% "zio-test"     % Version.zio % Test,
          "dev.zio"                 %% "zio-test-sbt" % Version.zio % Test
        ),
        buildInfoKeys := Seq(
          scalaBinaryVersion
        )
      )
    )
    .customRow(
      true,
      ScalaVersions,
      Seq(VirtualAxis.js),
      _.enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
        .settings(
          scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
          Compile / npmDependencies += "grpc-web" -> "1.4.2",
          scalaJSUseMainModuleInitializer         := true,
          libraryDependencies ++= Seq(
            "dev.zio" %%% "zio-test" % Version.zio
          ),
          webpack / version                       := "5.88.0",
          startWebpackDevServer / version         := "4.15.1"
        )
    )
    .settings(stdSettings)
    .settings(
      publish / skip := true
    )

lazy val benchmarks =
  projectMatrix
    .in(file("benchmarks"))
    .dependsOn(core)
    .defaultAxes()
    .enablePlugins(LocalCodeGenPlugin)
    .jvmPlatform(ScalaVersions)
    .settings(stdSettings)
    .settings(
      crossScalaVersions   := Seq(Scala212, Scala213),
      publish / skip       := true,
      libraryDependencies ++= Seq(
        "dev.zio"              %% "zio"                  % Version.zio,
        "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
        "io.grpc"               % "grpc-netty"           % Version.grpc
      ),
      Compile / PB.targets := Seq(
        scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
        genModule(
          "scalapb.zio_grpc.ZioCodeGenerator$"
        )                        -> (Compile / sourceManaged).value
      ),
      PB.protocVersion     := "3.13.0",
      codeGenClasspath     := (codeGenJVM212 / Compile / fullClasspath).value
    )

lazy val docs = project
  .enablePlugins(LocalCodeGenPlugin)
  .in(file("zio-grpc-docs"))
  .dependsOn(core.jvm(Scala213))
  .settings(
    scalaVersion                                       := Scala213,
    publish / skip                                     := true,
    moduleName                                         := "zio-grpc-docs",
    mdocVariables                                      := Map(
      "sbtProtocVersion" -> "1.0.6",
      "grpcVersion"      -> "1.50.1",
      "zioGrpcVersion"   -> "0.6.0-rc6",
      "scalapbVersion"   -> scalapb.compiler.Version.scalapbVersion
    ),
    libraryDependencies ++= Seq(
      "io.grpc"               % "grpc-netty"           % Version.grpc,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    ),
    libraryDependencySchemes += "com.thesamet.scalapb" %% "scalapb-runtime" % "always",
    Compile / PB.targets                               := Seq(
      scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
      genModule(
        "scalapb.zio_grpc.ZioCodeGenerator$"
      )                        -> (Compile / sourceManaged).value
    ),
    codeGenClasspath                                   := (codeGenJVM212 / Compile / fullClasspath).value
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
