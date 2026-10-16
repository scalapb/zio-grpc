import Settings.stdSettings

val Scala3 = "3.3.8"

val Scala213 = "2.13.18"

val Scala212 = "2.12.21"

val ScalaVersions = Seq(Scala212, Scala213, Scala3)

versionScheme := Some("early-semver")

LocalRootProject / publish / skip := true

Seq(
  organization := "com.thesamet.scalapb.zio-grpc",
  homepage     := Some(uri("https://github.com/scalapb/zio-grpc")),
  licenses     := List(
    "Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0")
  ),
  developers   := List(
    Developer(
      "thesamet",
      "Nadav Samet",
      "thesamet@gmail.com",
      uri("https://www.thesamet.com")
    )
  )
)

lazy val browserSettings = Seq(
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
)

lazy val core = projectMatrix
  .in(file("core"))
  .defaultAxes()
  .settings(stdSettings)
  .settings(
    name := "zio-grpc-core",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % Version.zio,
      "dev.zio" %% "zio-streams"  % Version.zio,
      "dev.zio" %% "zio-test"     % Version.zio % "test",
      "dev.zio" %% "zio-test-sbt" % Version.zio % "test"
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
    _.enablePlugins(ScalaJSPlugin)
      .settings(browserSettings)
      .settings(
        libraryDependencies ++= Seq(
          "com.thesamet.scalapb.grpcweb" %% "scalapb-grpcweb" % "0.7.0",
          "io.github.cquiroz"            %% "scala-java-time" % "2.7.0" % "test"
        )
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

lazy val codeGenJVM3 = codeGen.jvm(Scala3)

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
          "com.thesamet.scalapb.grpcweb" %% "scalapb-grpcweb" % "0.7.0",
          "io.github.cquiroz"            %% "scala-java-time" % "2.7.0"
        )
      )
    )
    .settings(stdSettings)
    .settings(
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
      codeGenClasspath     := (codeGenJVM3 / Compile / fullClasspath).value
    )

lazy val e2e =
  projectMatrix
    .in(file("e2e"))
    .dependsOn(core, e2eProtos)
    .defaultAxes()
    .jvmPlatform(
      ScalaVersions,
      settings = Seq(
        Test / fork := true,
        Test / javaOptions ++= Seq("-Xmx2G", "-XX:MaxDirectMemorySize=512m"),
        libraryDependencies ++= Seq(
          "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
          "io.grpc"               % "grpc-netty"           % Version.grpc,
          "io.grpc"               % "grpc-inprocess"       % Version.grpc % Test
        )
      )
    )
    .customRow(
      true,
      ScalaVersions,
      Seq(VirtualAxis.js),
      _.enablePlugins(ScalaJSPlugin)
        .settings(browserSettings)
        .settings(
          scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
          scalaJSUseMainModuleInitializer := true
        )
    )
    .settings(stdSettings)
    .settings(
      publish / skip       := true,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-test"     % Version.zio % "test",
        "dev.zio" %% "zio-test-sbt" % Version.zio % "test"
      ),
      Compile / run / fork := true,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )

lazy val installPlaywright = taskKey[Unit]("Install Chromium matching the Java Playwright dependency")

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
          "com.microsoft.playwright" % "playwright"   % Version.playwright % Test,
          "dev.zio"                 %% "zio-test"     % Version.zio        % Test,
          "dev.zio"                 %% "zio-test-sbt" % Version.zio        % Test
        ),
        buildInfoKeys     := Seq(
          scalaBinaryVersion
        ),
        installPlaywright := Def.uncached {
          val args = Seq("install") ++
            (if (sys.env.get("PLAYWRIGHT_INSTALL_DEPS").contains("true")) Seq("--with-deps") else Nil) ++
            Seq("chromium")
          (Test / runner).value
            .run(
              "com.microsoft.playwright.CLI",
              (Test / fullClasspath).value.map(a => fileConverter.value.toPath(a.data)),
              args,
              streams.value.log
            )
            .get
        }
      )
    )
    .customRow(
      true,
      ScalaVersions,
      Seq(VirtualAxis.js),
      _.enablePlugins(ScalaJSPlugin)
        .settings(browserSettings)
        .settings(
          Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
            (LocalRootProject / baseDirectory).value / "e2e-web" / "target" / s"js-${scalaBinaryVersion.value}" / "main"
        )
        .settings(
          scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
          scalaJSUseMainModuleInitializer := true,
          libraryDependencies ++= Seq(
            "dev.zio" %% "zio-test" % Version.zio
          )
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
      codeGenClasspath     := (codeGenJVM3 / Compile / fullClasspath).value
    )

lazy val docs = project
  .enablePlugins(LocalCodeGenPlugin)
  .in(file("zio-grpc-docs"))
  .dependsOn(core.jvm(Scala213))
  .settings(
    scalaVersion                                       := Scala213,
    publish / skip                                     := true,
    moduleName                                         := "zio-grpc-docs",
    mdocOut                                            := (LocalRootProject / baseDirectory).value / "zio-grpc-docs" / "target" / "mdoc",
    mdocVariables                                      := Map(
      "sbtProtocVersion" -> "1.1.0-RC2",
      "grpcVersion"      -> Version.grpc,
      "zioGrpcVersion"   -> "0.6.3",
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
    codeGenClasspath                                   := (codeGenJVM3 / Compile / fullClasspath).value
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
