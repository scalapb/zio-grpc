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
    private val clientWithMetadataServiceName  = OuterObject / (service.name + "ClientWithMetadata")
    private val accessorsClassName             = OuterObject / (service.name + "Accessors")
    private val accessorsWithMetadataClassName = OuterObject / (service.name + "WithMetadataAccessors")

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
        contextType: String
    ): String = {
      val reqType      = methodInType(method)
      val scalaOutType = method.outputType.scalaType

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType, context: $contextType): ${io(scalaOutType, "Any")}"
        case StreamType.ClientStreaming =>
          s"(request: $reqType, context: $contextType): ${io(scalaOutType, "Any")}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType, context: $contextType): ${stream(scalaOutType, "Any")}"
        case StreamType.Bidirectional   =>
          s"(request: $reqType, context: $contextType): ${stream(scalaOutType, "Any")}"
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

    def clientWithMetadataMethodSignature(
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
        contextType: String
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

    def printClientWithMetadataMethodSignature(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        clientWithMetadataMethodSignature(
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
          contextType = "Context1"
        ) + " = " + newImpl
      )
    }

    def printClientWithMetadataTransform(
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
        clientWithMetadataMethodSignature(
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
              "// Returns a client that gives access to headers and trailers",
              s"def withMetadata: ${clientWithMetadataServiceName.name}.ZService[Context1] = self.withMetadata.transform[Context1](f)"
            )
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def mapCallOptionsZIO(cf: $CallOptions => zio.IO[$Status, $CallOptions]): Service = transform[Context, Context1](self.mapCallOptionsZIO(cf), f)",
              s"def withMetadataZIO(headersEffect: zio.IO[$Status, $SafeMetadata]): Service = transform[Context, Context1](self.withMetadataZIO(headersEffect), f)",
              s"def withCallOptionsZIO(callOptions: zio.IO[$Status, $CallOptions]): Service = transform[Context, Context1](self.withCallOptionsZIO(callOptions), f)"
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
            printClientWithMetadataTransform
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
              contextType = "Context"
            )
          )
      ).add("}")
        .add(
          s"type ${traitName.name} = ${ztraitName.name}[Any]",
          s"type RC${traitName.name} = ${ztraitName.name}[$RequestContext]"
        )
        .add("")
        .add(s"object ${ztraitName.name} {")
        .indented(
          _.call(serverTranformableService)
            .add(
              s"implicit def ops[C](service: ${ztraitName.fullName}[C]): scalapb.zio_grpc.TransformableService.TransformableServiceOps[${ztraitName.fullName}, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[${ztraitName.fullName}, C](service)",
              s"implicit val genericBindable: scalapb.zio_grpc.GenericBindable[${ztraitName.fullName}] = new scalapb.zio_grpc.GenericBindable[${ztraitName.fullName}] {"
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
        .pipe(genClientWithMetadata)

    def genClientWithMetadata(fp: FunctionalPrinter): FunctionalPrinter =
      fp
        .add(
          s"type ${clientWithMetadataServiceName.name} = ${clientWithMetadataServiceName.name}.Service"
        )
        .add("")
        .add("// accessor with metadata methods")
        .add(
          s"class ${accessorsWithMetadataClassName.name}(callOptions: zio.IO[$Status, $CallOptions]) extends scalapb.zio_grpc.CallOptionsMethods[${accessorsWithMetadataClassName.name}] {"
        )
        .indented(
          _.add(s"def this() = this(zio.ZIO.succeed($CallOptions.DEFAULT))")
            .print(service.getMethods().asScala.toVector)(printAccessorWithMetadata)
            .add(
              s"def mapCallOptionsZIO(f: $CallOptions => zio.IO[$Status, $CallOptions]) = new ${accessorsWithMetadataClassName.name}(callOptions.flatMap(f))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"object ${clientWithMetadataServiceName.name} extends ${accessorsWithMetadataClassName.name}(zio.ZIO.succeed($CallOptions.DEFAULT)) {"
        )
        .indent
        .add(
          s"trait Service extends scalapb.zio_grpc.CallOptionsMethods[Service] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithMetadataMethodSignature
          )
            .add("")
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def withMetadataZIO(headersEffect: zio.IO[$Status, $SafeMetadata]): Service",
              s"def withCallOptionsZIO(callOptions: zio.IO[$Status, $CallOptions]): Service"
            )
        )
        .add("}")
        // .call(clientWithMetadataTranformableService)
        .add(
          s"// implicit def ops[C](service: Service): scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, C](service)"
        )
        .add("")
        .add("")
        .add(
          s"private[this] class ServiceStub(channel: $ZChannel, options: zio.IO[$Status, $CallOptions], headers: zio.IO[$Status, $SafeMetadata])"
        )
        .add(s"    extends ${clientWithMetadataServiceName.name}.Service {")
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithMetadataImpl
          )
            .add(
              s"def mapCallOptionsZIO(f: $CallOptions => zio.IO[$Status, $CallOptions]): Service = new ServiceStub(channel, options.flatMap(f), headers)",
              s"override def withMetadataZIO(headersEffect: zio.IO[$Status, $SafeMetadata]): Service = new ServiceStub(channel, options, headersEffect)",
              s"def withCallOptionsZIO(callOptions: zio.IO[$Status, $CallOptions]): Service = new ServiceStub(channel, callOptions, headers)"
            )
        )
        .add("}")
        .add("")
        .add(
          s"def scoped(managedChannel: $ZManagedChannel, options: zio.IO[$Status, $CallOptions] = zio.ZIO.succeed($CallOptions.DEFAULT), headers: zio.IO[$Status, $SafeMetadata]=$SafeMetadata.make): zio.ZIO[zio.Scope, Throwable, ${clientWithMetadataServiceName.name}.Service] = managedChannel.map {"
        )
        .add("  channel => new ServiceStub(channel, options, headers)")
        .add("}")
        .add("")
        .add(
          s"def live[Context: zio.Tag](managedChannel: $ZManagedChannel, options: zio.IO[$Status, $CallOptions]=zio.ZIO.succeed($CallOptions.DEFAULT), headers: zio.IO[$Status, $SafeMetadata] = $SafeMetadata.make): zio.ZLayer[Any, Throwable, ${clientWithMetadataServiceName.name}.Service] = zio.ZLayer.scoped(scoped(managedChannel, options, headers))"
        )
        .outdent
        .add("}")

    def genClient(fp: FunctionalPrinter): FunctionalPrinter =
      fp
        .add(
          s"type ${clientServiceName.name} = ${clientServiceName.name}.Service"
        )
        .add("")
        .add("// accessor methods")
        .add(
          s"class ${accessorsClassName.name}(callOptions: zio.IO[$Status, $CallOptions]) extends scalapb.zio_grpc.CallOptionsMethods[${accessorsClassName.name}] {"
        )
        .indented(
          _.add(s"def this() = this(zio.ZIO.succeed($CallOptions.DEFAULT))")
            .print(service.getMethods().asScala.toVector)(printAccessor)
            .add(
              s"def mapCallOptionsZIO(f: $CallOptions => zio.IO[$Status, $CallOptions]) = new ${accessorsClassName.name}(callOptions.flatMap(f))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"object ${clientServiceName.name} extends ${accessorsClassName.name}(zio.ZIO.succeed($CallOptions.DEFAULT)) {"
        )
        .indent
        .add(
          s"trait Service extends scalapb.zio_grpc.CallOptionsMethods[Service] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientMethodSignature(
              outEnvType = "Any"
            )
          )
            .add("")
            .add(
              "// Returns a client that gives access to headers and trailers",
              s"def withMetadata: ${clientWithMetadataServiceName.name}.Service"
            )
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def withMetadataZIO(headersEffect: zio.IO[$Status, $SafeMetadata]): Service",
              s"def withCallOptionsZIO(callOptions: zio.IO[$Status, $CallOptions]): Service"
            )
        )
        .add("}")
        // .call(clientTranformableService)
        .add(
          // s"implicit def ops[C](service: Service): scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[Service, C](service)"
        )
        .add("")
        .add("")
        .add(
          s"private[this] class ServiceStub(underlying: ${clientWithMetadataServiceName.name}.Service)"
        )
        .add(s"    extends ${clientServiceName.name}.Service {")
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientImpl(envType = "Any")
          )
            .add(
              "// Returns a client that gives access to headers and trailers",
              s"def withMetadata: ${clientWithMetadataServiceName.name}.Service = underlying"
            )
            .add(
              s"def mapCallOptionsZIO(f: $CallOptions => zio.IO[$Status, $CallOptions]): Service = new ServiceStub(underlying.mapCallOptionsZIO(f))",
              s"override def withMetadataZIO(headersEffect: zio.IO[$Status, $SafeMetadata]): Service = new ServiceStub(underlying.withMetadataZIO(headersEffect))",
              s"def withCallOptionsZIO(callOptions: zio.IO[$Status, $CallOptions]): Service = new ServiceStub(underlying.withCallOptionsZIO(callOptions))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"def scoped(managedChannel: $ZManagedChannel, options: zio.IO[$Status, $CallOptions] = zio.ZIO.succeed($CallOptions.DEFAULT), headers: zio.IO[$Status, $SafeMetadata]=$SafeMetadata.make): zio.ZIO[zio.Scope, Throwable, ${clientServiceName.name}.Service] = ${clientWithMetadataServiceName.name}.scoped(managedChannel, options, headers).map(client => new ServiceStub(client))"
        )
        .add("")
        .add(
          s"def live(managedChannel: $ZManagedChannel, options: zio.IO[$Status, $CallOptions]=zio.ZIO.succeed($CallOptions.DEFAULT), headers: zio.IO[$Status, $SafeMetadata] = $SafeMetadata.make): zio.ZLayer[Any, Throwable, ${clientServiceName.name}.Service] = ${clientWithMetadataServiceName.name}.live(managedChannel, options, headers).map(clientEnv => zio.ZEnvironment(new ServiceStub(clientEnv.get)))"
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
          contextType = s"${clientServiceName.name}.Service"
        ) + " = "
      val innerCall         = s"_.withCallOptionsZIO(callOptions).${method.name}(request)"
      val clientCall        = method.streamType match {
        case StreamType.Unary           =>
          s"_root_.zio.ZIO.serviceWithZIO[${clientServiceName.name}.Service]($innerCall)"
        case StreamType.ClientStreaming =>
          s"_root_.zio.ZIO.serviceWithZIO[${clientServiceName.name}.Service]($innerCall)"
        case StreamType.ServerStreaming =>
          s"_root_.zio.stream.ZStream.serviceWithStream[${clientServiceName.name}.Service]($innerCall)"
        case StreamType.Bidirectional   =>
          s"_root_.zio.stream.ZStream.serviceWithStream[${clientServiceName.name}.Service]($innerCall)"
      }
      fp.add(sigWithoutContext + clientCall)
    }

    def printAccessorWithMetadata(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val sigWithoutContext =
        clientWithMetadataMethodSignature(
          method,
          envOutType = s"${clientWithMetadataServiceName.name}.Service"
        ) + " = "
      val innerCall         = s"_.withCallOptionsZIO(callOptions).${method.name}(request)"
      val clientCall        = method.streamType match {
        case StreamType.Unary           =>
          s"_root_.zio.ZIO.serviceWithZIO[${clientWithMetadataServiceName.name}.Service]($innerCall)"
        case StreamType.ClientStreaming =>
          s"_root_.zio.ZIO.serviceWithZIO[${clientWithMetadataServiceName.name}.Service]($innerCall)"
        case StreamType.ServerStreaming =>
          s"_root_.zio.stream.ZStream.serviceWithStream[${clientWithMetadataServiceName.name}.Service]($innerCall)"
        case StreamType.Bidirectional   =>
          s"_root_.zio.stream.ZStream.serviceWithStream[${clientWithMetadataServiceName.name}.Service]($innerCall)"
      }
      fp.add(sigWithoutContext + clientCall)
    }

    def printClientWithMetadataImpl(fp: FunctionalPrinter, method: MethodDescriptor): FunctionalPrinter = {
      val clientCall = method.streamType match {
        case StreamType.Unary           => s"$ClientCalls.withMetadata.unaryCall"
        case StreamType.ClientStreaming => s"$ClientCalls.withMetadata.clientStreamingCall"
        case StreamType.ServerStreaming => s"$ClientCalls.withMetadata.serverStreamingCall"
        case StreamType.Bidirectional   => s"$ClientCalls.withMetadata.bidiCall"
      }
      val prefix     = method.streamType match {
        case StreamType.Unary           => s"headers.zip(options).flatMap"
        case StreamType.ClientStreaming => s"headers.zip(options).flatMap"
        case StreamType.ServerStreaming =>
          s"zio.stream.ZStream.fromZIO(headers.zip(options)).flatMap"
        case StreamType.Bidirectional   =>
          s"zio.stream.ZStream.fromZIO(headers.zip(options)).flatMap"
      }
      fp.add(
        clientWithMetadataMethodSignature(
          method,
          envOutType = "Any"
        ) + s" = $prefix { case (headers, options) => $clientCall("
      ).indent
        .add(
          s"channel, ${method.grpcDescriptor.fullName}, options,"
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
