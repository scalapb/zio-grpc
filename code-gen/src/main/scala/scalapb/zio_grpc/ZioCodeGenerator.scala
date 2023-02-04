package scalapb.zio_grpc

import com.google.protobuf.ExtensionRegistry
import scalapb.options.Scalapb
import scalapb.compiler.ProtobufGenerator
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import com.google.protobuf.Descriptors.FileDescriptor
import scalapb.compiler.DescriptorImplicits
import com.google.protobuf.Descriptors.ServiceDescriptor
import com.google.protobuf.Descriptors.MethodDescriptor
import scalapb.compiler.StreamType
import scalapb.compiler.FunctionalPrinter
import protocgen.CodeGenApp
import protocgen.CodeGenResponse
import protocgen.CodeGenRequest
import scalapb.compiler.NameUtils

import scala.jdk.CollectionConverters._
import scala.util.chaining._

object ZioCodeGenerator extends CodeGenApp {
  override def registerExtensions(registry: ExtensionRegistry): Unit =
    Scalapb.registerAllExtensions(registry)

  override def suggestedDependencies: Seq[Artifact] =
    Seq(
      Artifact(
        "com.thesamet.scalapb.zio-grpc",
        "zio-grpc-core",
        BuildInfo.version,
        crossVersion = true
      )
    )

  def process(request: CodeGenRequest): CodeGenResponse =
    ProtobufGenerator.parseParameters(request.parameter) match {
      case Right(params) =>
        val implicits =
          DescriptorImplicits.fromCodeGenRequest(params, request)
        CodeGenResponse.succeed(
          request.filesToGenerate.collect {
            case file if !file.getServices().isEmpty() =>
              new ZioFilePrinter(implicits, file).result()
          },
          Set(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL)
        )
      case Left(error)   =>
        CodeGenResponse.fail(error)
    }
}

class ZioFilePrinter(
    implicits: DescriptorImplicits,
    file: FileDescriptor
) {
  import implicits._

  val Channel             = "io.grpc.Channel"
  val CallOptions         = "io.grpc.CallOptions"
  val ClientCalls         = "scalapb.zio_grpc.client.ClientCalls"
  val Duration            = "zio.Duration"
  val SafeMetadata        = "scalapb.zio_grpc.SafeMetadata"
  val Status              = "io.grpc.Status"
  val Deadline            = "io.grpc.Deadline"
  val methodDescriptor    = "io.grpc.MethodDescriptor"
  val RequestContext      = "scalapb.zio_grpc.RequestContext"
  val ZClientCall         = "scalapb.zio_grpc.client.ZClientCall"
  val ZManagedChannel     = "scalapb.zio_grpc.ZManagedChannel"
  val ZChannel            = "scalapb.zio_grpc.ZChannel"
  val Nanos               = "java.util.concurrent.TimeUnit.NANOSECONDS"
  val serverServiceDef    = "_root_.io.grpc.ServerServiceDefinition"
  private val OuterObject =
    file.scalaPackage / s"Zio${NameUtils.snakeCaseToCamelCase(baseName(file.getName), true)}"

  def scalaFileName =
    OuterObject.fullName.replace('.', '/') + ".scala"

  def content: String = {
    val fp = new FunctionalPrinter()
    fp.add(
      s"package ${file.scalaPackage.fullName}",
      "",
      "import scala.language.implicitConversions",
      "",
      s"object ${OuterObject.name} {"
    ).indent
      .print(file.getServices().asScala)((fp, s) => new ServicePrinter(s).print(fp))
      .outdent
      .add("}")
      .result()
  }

  def result(): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(scalaFileName)
    b.setContent(content)
    b.build()
  }

  class ServicePrinter(service: ServiceDescriptor) {

    private val traitName  = OuterObject / service.name
    private val ztraitName = OuterObject / ("Z" + service.name)

    private val clientServiceName              = OuterObject / (service.name + "Client")
    private val clientWithResponseMetadataServiceName  = OuterObject / (service.name + "ClientWithResponseMetadata")
    private val accessorsClassName             = OuterObject / (service.name + "Accessors")
    private val accessorsWithResponseMetadataClassName = OuterObject / (service.name + "WithResponseMetadataAccessors")

    def methodInType(method: MethodDescriptor): String = {
      val scalaInType = method.inputType.scalaType

      method.streamType match {
        case StreamType.Unary           =>
          scalaInType
        case StreamType.ClientStreaming =>
          stream(scalaInType, "Any")
        case StreamType.ServerStreaming =>
          scalaInType
        case StreamType.Bidirectional   =>
          stream(scalaInType, "Any")
      }
    }

    def methodSignature(
        method: MethodDescriptor,
        contextType: Option[String]
    ): String = {
      val reqType      = methodInType(method)
      val scalaOutType = method.outputType.scalaType
      val contextParam = contextType.fold("")(ctx => s""", context: $ctx""")

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType$contextParam): ${io(scalaOutType, "Any")}"
        case StreamType.ClientStreaming =>
          s"(request: $reqType$contextParam): ${io(scalaOutType, "Any")}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType$contextParam): ${stream(scalaOutType, "Any")}"
        case StreamType.Bidirectional   =>
          s"(request: $reqType$contextParam): ${stream(scalaOutType, "Any")}"
      })
    }

    def clientMethodSignature(
        method: MethodDescriptor,
        contextType: String
    ): String = {
      val reqType      = methodInType(method)
      val scalaOutType = method.outputType.scalaType

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType): ${io(scalaOutType, contextType)}"
        case StreamType.ClientStreaming =>
          s"(request: $reqType): ${io(scalaOutType, contextType)}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType): ${stream(scalaOutType, contextType)}"
        case StreamType.Bidirectional   =>
          s"(request: $reqType): ${stream(scalaOutType, contextType)}"
      })
    }

    def clientWithResponseMetadataSignature(
        method: MethodDescriptor,
        envOutType: String
    ): String = {
      val reqType       = methodInType(method)
      val scalaOutType  = method.outputType.scalaType
      val ioOutType     = s"scalapb.zio_grpc.ResponseContext[$scalaOutType]"
      val streamOutType = s"scalapb.zio_grpc.ResponseFrame[$scalaOutType]"

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType): ${io(ioOutType, envOutType)}"
        case StreamType.ClientStreaming =>
          s"(request: $reqType): ${io(ioOutType, envOutType)}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType): ${stream(streamOutType, envOutType)}"
        case StreamType.Bidirectional   =>
          s"(request: $reqType): ${stream(streamOutType, envOutType)}"
      })
    }

    def printMethodSignature(
        contextType: Option[String]
    )(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        methodSignature(
          method,
          contextType
        )
      )

    def printClientMethodSignature(
        outEnvType: String
    )(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        clientMethodSignature(
          method,
          outEnvType
        )
      )

    def printClientWithResponseMetadataMethodSignature(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        clientWithResponseMetadataSignature(
          method,
          "Any"
        )
      )

    def printServerTransform(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val delegate = s"self.${method.name}"
      val newImpl  = method.streamType match {
        case StreamType.Unary | StreamType.ClientStreaming         =>
          s"f.effect($delegate(request, _))(context)"
        case StreamType.ServerStreaming | StreamType.Bidirectional =>
          s"f.stream($delegate(request, _))(context)"
      }
      fp.add(
        methodSignature(
          method,
          contextType = Some("Context1")
        ) + " = " + newImpl
      )
    }

    def printClientWithResponseMetadataTransform(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val delegate = s"self.${method.name}"
      val newImpl  = method.streamType match {
        case StreamType.Unary           =>
          s"f.effect($delegate(request))"
        case StreamType.ServerStreaming =>
          s"f.stream($delegate(request))"
        case StreamType.ClientStreaming =>
          s"""zio.ZIO.fail($Status.INTERNAL.withDescription("Transforming client-side client-streaming calls is not supported"))"""
        case StreamType.Bidirectional   =>
          s"""zio.stream.ZStream.fail($Status.INTERNAL.withDescription("Transforming client-side bidi calls is not supported"))"""
      }
      fp.add(
        clientWithResponseMetadataSignature(
          method,
          "Any"
        ) + " = " + newImpl
      )
    }

    def printClientTransform(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val delegate = s"self.${method.name}"
      val newImpl  = method.streamType match {
        case StreamType.Unary           =>
          s"f.effect($delegate(request))"
        case StreamType.ServerStreaming =>
          s"f.stream($delegate(request))"
        case StreamType.ClientStreaming =>
          s"""zio.ZIO.fail($Status.INTERNAL.withDescription("Transforming client-side client-streaming calls is not supported"))"""
        case StreamType.Bidirectional   =>
          s"""zio.stream.ZStream.fail($Status.INTERNAL.withDescription("Transforming client-side bidi calls is not supported"))"""
      }
      fp.add(
        clientMethodSignature(
          method,
          contextType = "Context1"
        ) + " = " + newImpl
      )
    }

    def serverTranformableService(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"implicit val transformableService: scalapb.zio_grpc.TransformableService[${ztraitName.name}] = new scalapb.zio_grpc.TransformableService[${ztraitName.name}] {"
      ).indented(
        _.add(
          s"def transform[Context, Context1](self: ${ztraitName.name}[Context], f: scalapb.zio_grpc.ZTransform[Context, $Status, Context1]): ${ztraitName.fullName}[Context1] = new ${ztraitName.fullName}[Context1] {"
        ).indented(
          _.print(service.getMethods().asScala.toVector)(
            printServerTransform
          )
        ).add("}")
      ).add("}")

    def clientTranformableService(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"implicit val transformableService: scalapb.zio_grpc.TransformableService[ZService] = new scalapb.zio_grpc.TransformableService[ZService] {"
      ).indented(
        _.add(
          s"def transform[Context, Context1](self: ZService[Context], f: scalapb.zio_grpc.ZTransform[Context, $Status, Context1]): ZService[Context1] = new ZService[Context1] {"
        ).indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientTransform
          )
            .add(
              "// Returns a client that gives access to headers and trailers from server's response",
              s"def withResponseMetadata: ${clientWithResponseMetadataServiceName.name}.ZService[Context1] = self.withMetadata.transform[Context1](f)"
            )
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def mapCallOptions(cf: $CallOptions => $CallOptions): Service = transform[Context, Context1](self.mapCallOptions(cf), f)",
            )
        ).add("}")
      ).add("}")

    def clientWithMetadataTranformableService(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"implicit val transformableService: scalapb.zio_grpc.TransformableService[ZService] = new scalapb.zio_grpc.TransformableService[ZService] {"
      ).indented(
        _.add(
          s"def transform[Context, Context1](self: ZService[Context], f: scalapb.zio_grpc.ZTransform[Context, $Status, Context1]): ZService[Context1] = new ZService[Context1] {"
        ).indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithResponseMetadataTransform
          )
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def mapCallOptionsZIO(cf: $CallOptions => zio.IO[$Status, $CallOptions]): ZService[Context1] = transform[Context, Context1](self.mapCallOptionsZIO(cf), f)",
              s"def withMetadataZIO(headersEffect: zio.IO[$Status, $SafeMetadata]): ZService[Context1] = transform[Context, Context1](self.withMetadataZIO(headersEffect), f)",
              s"def withCallOptionsZIO(callOptions: zio.IO[$Status, $CallOptions]): ZService[Context1] = transform[Context, Context1](self.withCallOptionsZIO(callOptions), f)"
            )
        ).add("}")
      ).add("}")

    def print(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"trait ${ztraitName.name}[-Context] extends scalapb.zio_grpc.ZGeneratedService[Context, ${ztraitName.name}] {"
      ).indented(
        _.add("self =>")
          .print(service.getMethods().asScala.toVector)(
            printMethodSignature(
              contextType = Some("Context")
            )
          )
      ).add("}")
      .add("")
        .add(
          s"trait ${traitName.name} extends scalapb.zio_grpc.GeneratedService {"
        )
        .indented(
          _.add("self =>")
            .add(s"type WithContext[-C] = ${ztraitName.name}[C]")
            .print(service.getMethods().asScala.toVector)(
              printMethodSignature(contextType = None)
            )
            .add("")
            .add(s"override def withContext: ${ztraitName.name}[Any] = new ${ztraitName.name}[Any] {")
            .indented(
              _.print(service.getMethods().asScala.toVector) { (fp, method) =>
                (
                  fp.add(s"${methodSignature(method, contextType = Some("Any"))} = self.${method.name}(request)")
                )
              }
            )
            .add("}")
        )
        .add("}")
        .add("")
        .add(s"object ${traitName.name} {")
        .indented(
          _.add(
            s"implicit val genericBindable: scalapb.zio_grpc.GenericBindable[${traitName.fullName}] = new scalapb.zio_grpc.GenericBindable[${traitName.fullName}] {"
          )
            .indented(
              _.add(
                s"""def bind(serviceImpl: ${traitName.fullName}): zio.UIO[$serverServiceDef] = ${ztraitName.fullName}.genericBindable.bind(serviceImpl.withContext)"""
              )
            )
            .add("}")
        )
        .add("}")
        .add(
          s"type RC${traitName.name} = ${ztraitName.name}[$RequestContext]"
        )
        .add("")
        .add(s"object ${ztraitName.name} {")
        .indented(
          _.call(serverTranformableService)
            .add(
              s"implicit def ops[C](service: ${ztraitName.fullName}[C]): scalapb.zio_grpc.TransformableService.TransformableServiceOps[${ztraitName.fullName}, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[${ztraitName.fullName}, C](service)",
              s"implicit val genericBindable: scalapb.zio_grpc.GenericBindable[${ztraitName.fullName}[$RequestContext]] = new scalapb.zio_grpc.GenericBindable[${ztraitName.fullName}[$RequestContext]] {"
            )
            .indented(
              _.add(
                s"""def bind(serviceImpl: ${ztraitName.fullName}[$RequestContext]): zio.UIO[$serverServiceDef] ="""
              ).indent
                .add("zio.ZIO.runtime[Any].map {")
                .indent
                .add("runtime =>")
                .indent
                .add(
                  s"""$serverServiceDef.builder(${service.grpcDescriptor.fullName})"""
                )
                .print(service.getMethods().asScala.toVector)(
                  printBindService(_, _)
                )
                .add(".build()")
                .outdent
                .outdent
                .add("}")
                .outdent
                .add("}")
            )
        )
        .add("}")
        .add("")
        .pipe(genClient)
        .pipe(genClientWithResponseMetadata)

    def genClientWithResponseMetadata(fp: FunctionalPrinter): FunctionalPrinter =
      fp
        .add("")
        .add("// accessor with metadata methods")
        .add(
          s"class ${accessorsWithResponseMetadataClassName.name}(callOptionsFunc: $CallOptions => $CallOptions, metadataFunc: $SafeMetadata => zio.UIO[$SafeMetadata]) extends scalapb.zio_grpc.ClientMethods[${accessorsWithResponseMetadataClassName.name}] {"
        )
        .indented(
          _.add(s"def this() = this(identity, zio.ZIO.succeed(_))")
            .print(service.getMethods().asScala.toVector)(printAccessorWithResponseMetadata)
            .add(
              "// Returns new instance with modified call options. See ClientMethods for more.",
              s"override def mapCallOptions(f: $CallOptions => $CallOptions): ${accessorsWithResponseMetadataClassName.name} = new ${accessorsWithResponseMetadataClassName.name}(co => f(callOptionsFunc(co)), metadataFunc)",
              "// Returns new instance with modified metadata. See ClientMethods for more.",
              s"override def mapMetadataZIO(f: $SafeMetadata => zio.UIO[$SafeMetadata]): ${accessorsWithResponseMetadataClassName.name} = new ${accessorsWithResponseMetadataClassName.name}(callOptionsFunc, md => metadataFunc(md).flatMap(f))",
            )
        )
        .add("}")
        .add("")
        .add(
          s"trait ${clientWithResponseMetadataServiceName.name} extends scalapb.zio_grpc.ClientMethods[${clientWithResponseMetadataServiceName.name}] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithResponseMetadataMethodSignature
          )
        )
        .add("}")
        .add("")
        .add(
          s"object ${clientWithResponseMetadataServiceName.name} extends ${accessorsWithResponseMetadataClassName.name} {"
        )
        .indent
        // .call(clientWithMetadataTranformableService)
        .add(
          s"// implicit def ops[C](service: Service): scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, C](service)"
        )
        .add(
          s"private[this] class ServiceStub(channel: $ZChannel, callOptions: $CallOptions, metadata: zio.UIO[$SafeMetadata])"
        )
        .add(s"    extends ${clientWithResponseMetadataServiceName.name} {")
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithResponseMetadataImpl
          )
            .add(
              "// Returns new instance with modified call options. See ClientMethods for more.",
              s"override def mapCallOptions(f: $CallOptions => $CallOptions): ${clientWithResponseMetadataServiceName.name} = new ServiceStub(channel, f(callOptions), metadata)",
              "// Returns new instance with modified metadata. See ClientMethods for more.",
              s"override def mapMetadataZIO(f: $SafeMetadata => zio.UIO[$SafeMetadata]): ${clientWithResponseMetadataServiceName.name} = new ServiceStub(channel, callOptions, metadata.flatMap(f))",
            )
        )
        .add("}")
        .add("")
        .add(
          s"def scoped(managedChannel: $ZManagedChannel, options: $CallOptions = $CallOptions.DEFAULT, metadata: zio.UIO[$SafeMetadata]=$SafeMetadata.make): zio.ZIO[zio.Scope, Throwable, ${clientWithResponseMetadataServiceName.name}] = managedChannel.map {"
        )
        .add("  channel => new ServiceStub(channel, options, metadata)")
        .add("}")
        .add("")
        .add(
          s"def live[Context](managedChannel: $ZManagedChannel, options: $CallOptions=$CallOptions.DEFAULT, metadata: zio.UIO[$SafeMetadata] = $SafeMetadata.make): zio.ZLayer[Any, Throwable, ${clientWithResponseMetadataServiceName.name}] = zio.ZLayer.scoped(scoped(managedChannel, options, metadata))"
        )
        .outdent
        .add("}")

    def genClient(fp: FunctionalPrinter): FunctionalPrinter =
      fp
        .add("")
        .add("// accessor methods")
        .add(
          s"class ${accessorsClassName.name}(callOptionsFunc: $CallOptions => $CallOptions, metadataFunc: $SafeMetadata => zio.UIO[$SafeMetadata]) extends scalapb.zio_grpc.ClientMethods[${accessorsClassName.name}] {"
        )
        .indented(
          _.add(s"def this() = this(identity, zio.ZIO.succeed(_))")
            .print(service.getMethods().asScala.toVector)(printAccessor)
            .add(
              "// Returns new instance with modified call options. See ClientMethods for more.",
              s"override def mapCallOptions(f: $CallOptions => $CallOptions): ${accessorsClassName.name} = new ${accessorsClassName.name}(co => f(callOptionsFunc(co)), metadataFunc)",
              "// Returns new instance with modified metadata. See ClientMethods for more.",
              s"override def mapMetadataZIO(f: $SafeMetadata => zio.UIO[$SafeMetadata]): ${accessorsClassName.name} = new ${accessorsClassName.name}(callOptionsFunc, md => metadataFunc(md).flatMap(f))",
            )
        )
        .add("}")
        .add("")
        .add(
          s"trait ${clientServiceName.name} extends scalapb.zio_grpc.ClientMethods[${clientServiceName.name}] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientMethodSignature(
              outEnvType = "Any"
            )
          )
            .add("")
            .add(
              "// Returns a client that gives access to headers and trailers from server's response",
              s"def withResponseMetadata: ${clientWithResponseMetadataServiceName.name}"
            )
        )
        .add("}")
        .add("")
        .add(
          s"object ${clientServiceName.name} extends ${accessorsClassName.name} {"
        )
        .indent
        // .call(clientTranformableService)
        .add(
          // s"implicit def ops[C](service: Service): scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[Service, C](service)"
        )
        .add("")
        .add(
          s"private[this] class ServiceStub(underlying: ${clientWithResponseMetadataServiceName.name})"
        )
        .add(s"    extends ${clientServiceName.name} {")
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientImpl(envType = "Any")
          )
            .add(
              "// Returns a client that gives access to headers and trailers from server's response",
              s"def withResponseMetadata: ${clientWithResponseMetadataServiceName.name} = underlying",
              "// Returns new instance with modified call options. See ClientMethods for more.",
              s"override def mapCallOptions(f: $CallOptions => $CallOptions): ${clientServiceName.name} = new ServiceStub(underlying.mapCallOptions(f))",
              "// Returns new instance with modified metadata. See ClientMethods for more.",
              s"override def mapMetadataZIO(f: $SafeMetadata => zio.UIO[$SafeMetadata]): ${clientServiceName.name} = new ServiceStub(underlying.mapMetadataZIO(f))",
            )
        )
        .add("}")
        .add("")
        .add(
          s"def scoped(managedChannel: $ZManagedChannel, options: $CallOptions = $CallOptions.DEFAULT, metadata: zio.UIO[$SafeMetadata]=$SafeMetadata.make): zio.ZIO[zio.Scope, Throwable, ${clientServiceName.name}] = ${clientWithResponseMetadataServiceName.name}.scoped(managedChannel, options, metadata).map(client => new ServiceStub(client))"
        )
        .add("")
        .add(
          s"def live(managedChannel: $ZManagedChannel, options: $CallOptions=$CallOptions.DEFAULT, metadata: zio.UIO[$SafeMetadata] = $SafeMetadata.make): zio.ZLayer[Any, Throwable, ${clientServiceName.name}] = ${clientWithResponseMetadataServiceName.name}.live(managedChannel, options, metadata).map(clientEnv => zio.ZEnvironment(new ServiceStub(clientEnv.get)))"
        )
        .outdent
        .add("}")

    def printAccessor(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val sigWithoutContext =
        clientMethodSignature(
          method,
          contextType = s"${clientServiceName.name}"
        ) + " = "
      val innerCall         = s"_.mapCallOptions(callOptionsFunc).mapMetadataZIO(metadataFunc).${method.name}(request)"
      val clientCall        = method.streamType match {
        case StreamType.Unary           =>
          s"_root_.zio.ZIO.serviceWithZIO[${clientServiceName.name}]($innerCall)"
        case StreamType.ClientStreaming =>
          s"_root_.zio.ZIO.serviceWithZIO[${clientServiceName.name}]($innerCall)"
        case StreamType.ServerStreaming =>
          s"_root_.zio.stream.ZStream.serviceWithStream[${clientServiceName.name}]($innerCall)"
        case StreamType.Bidirectional   =>
          s"_root_.zio.stream.ZStream.serviceWithStream[${clientServiceName.name}]($innerCall)"
      }
      fp.add(sigWithoutContext + clientCall)
    }

    def printAccessorWithResponseMetadata(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val sigWithoutContext =
        clientWithResponseMetadataSignature(
          method,
          envOutType = s"${clientWithResponseMetadataServiceName.name}"
        ) + " = "
      val innerCall         = s"_.mapCallOptions(callOptionsFunc).mapMetadataZIO(metadataFunc).${method.name}(request)"
      val clientCall        = method.streamType match {
        case StreamType.Unary           =>
          s"_root_.zio.ZIO.serviceWithZIO[${clientWithResponseMetadataServiceName.name}]($innerCall)"
        case StreamType.ClientStreaming =>
          s"_root_.zio.ZIO.serviceWithZIO[${clientWithResponseMetadataServiceName.name}]($innerCall)"
        case StreamType.ServerStreaming =>
          s"_root_.zio.stream.ZStream.serviceWithStream[${clientWithResponseMetadataServiceName.name}]($innerCall)"
        case StreamType.Bidirectional   =>
          s"_root_.zio.stream.ZStream.serviceWithStream[${clientWithResponseMetadataServiceName.name}]($innerCall)"
      }
      fp.add(sigWithoutContext + clientCall)
    }

    def printClientWithResponseMetadataImpl(fp: FunctionalPrinter, method: MethodDescriptor): FunctionalPrinter = {
      val clientCall = method.streamType match {
        case StreamType.Unary           => s"$ClientCalls.withMetadata.unaryCall"
        case StreamType.ClientStreaming => s"$ClientCalls.withMetadata.clientStreamingCall"
        case StreamType.ServerStreaming => s"$ClientCalls.withMetadata.serverStreamingCall"
        case StreamType.Bidirectional   => s"$ClientCalls.withMetadata.bidiCall"
      }
      val prefix     = method.streamType match {
        case StreamType.Unary           => s"metadata.flatMap"
        case StreamType.ClientStreaming => s"metadata.flatMap"
        case StreamType.ServerStreaming =>
          s"zio.stream.ZStream.fromZIO(metadata).flatMap"
        case StreamType.Bidirectional   =>
          s"zio.stream.ZStream.fromZIO(metadata).flatMap"
      }
      fp.add(
        clientWithResponseMetadataSignature(
          method,
          envOutType = "Any"
        ) + s" = $prefix { headers => $clientCall("
      ).indent
        .add(
          s"channel, ${method.grpcDescriptor.fullName}, callOptions,"
        )
        .add(s"headers,")
        .add(s"request")
        .outdent
        .add(s")}")
    }

    def printClientImpl(
        envType: String
    )(fp: FunctionalPrinter, method: MethodDescriptor): FunctionalPrinter = {
      val suffix = method.streamType match {
        case StreamType.Unary | StreamType.ClientStreaming         =>
          ".map(_.response)"
        case StreamType.ServerStreaming | StreamType.Bidirectional =>
          ".collect { case scalapb.zio_grpc.ResponseFrame.Message(message) => message }"
      }

      fp.add(
        clientMethodSignature(
          method,
          contextType = envType
        ) + s" = underlying.${method.name}(request)$suffix"
      )
    }

    def printBindService(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val CH = "_root_.scalapb.zio_grpc.server.ZServerCallHandler"

      val serverCall = (method.streamType match {
        case StreamType.Unary           => "unaryCallHandler"
        case StreamType.ClientStreaming => "clientStreamingCallHandler"
        case StreamType.ServerStreaming => "serverStreamingCallHandler"
        case StreamType.Bidirectional   => "bidiCallHandler"
      })

      fp.add(".addMethod(")
        .indent
        .add(
          s"${method.grpcDescriptor.fullName},"
        )
        .add(
          s"$CH.$serverCall(runtime, serviceImpl.${method.name}(_, _))"
        )
        .outdent
        .add(")")
    }
  }

  def stream(res: String, envType: String) =
    envType match {
      case "Any" => s"_root_.zio.stream.Stream[$Status, $res]"
      case r     => s"_root_.zio.stream.ZStream[$r, $Status, $res]"
    }

  def io(res: String, envType: String) =
    envType match {
      case "Any" => s"_root_.zio.IO[$Status, $res]"
      case r     => s"_root_.zio.ZIO[$r, $Status, $res]"
    }
}
