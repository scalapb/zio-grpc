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
  val Duration            = "zio.duration.Duration"
  val SafeMetadata        = "scalapb.zio_grpc.SafeMetadata"
  val Status              = "io.grpc.Status"
  val Deadline            = "io.grpc.Deadline"
  val methodDescriptor    = "io.grpc.MethodDescriptor"
  val RequestContext      = "scalapb.zio_grpc.RequestContext"
  val ZClientCall         = "scalapb.zio_grpc.client.ZClientCall"
  val ZManagedChannel     = "scalapb.zio_grpc.ZManagedChannel"
  val ZChannel            = "scalapb.zio_grpc.ZChannel"
  val ZBindableService    = "scalapb.zio_grpc.ZBindableService"
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

    private val CH = "_root_.scalapb.zio_grpc.server.ZServerCallHandler"

    def methodInType(method: MethodDescriptor, inEnvType: String): String = {
      val scalaInType = method.inputType.scalaType

      method.streamType match {
        case StreamType.Unary           =>
          scalaInType
        case StreamType.ClientStreaming =>
          stream(scalaInType, inEnvType)
        case StreamType.ServerStreaming =>
          scalaInType
        case StreamType.Bidirectional   =>
          stream(scalaInType, inEnvType)
      }
    }

    def methodSignature(
        method: MethodDescriptor,
        inEnvType: String,
        outEnvType: String
    ): String = {
      val reqType      = methodInType(method, inEnvType)
      val scalaOutType = method.outputType.scalaType

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType): ${io(scalaOutType, outEnvType)}"
        case StreamType.ClientStreaming =>
          s"(request: $reqType): ${io(scalaOutType, outEnvType)}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType): ${stream(scalaOutType, outEnvType)}"
        case StreamType.Bidirectional   =>
          s"(request: $reqType): ${stream(scalaOutType, outEnvType)}"
      })
    }

    def clientMethodSignature(
        method: MethodDescriptor,
        inEnvType: String,
        outEnvType: String
    ): String = {
      val reqType      = methodInType(method, inEnvType)
      val scalaOutType = method.outputType.scalaType

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType): ${io(scalaOutType, outEnvType)}"
        case StreamType.ClientStreaming =>
          s"[${inEnvType}](request: $reqType): ${io(scalaOutType, outEnvType + " with " + inEnvType)}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType): ${stream(scalaOutType, outEnvType)}"
        case StreamType.Bidirectional   =>
          s"[${inEnvType}](request: $reqType): ${stream(scalaOutType, outEnvType + " with " + inEnvType)}"
      })
    }

    def clientWithMetadataMethodSignature(
        method: MethodDescriptor,
        inEnvType: String,
        outEnvType: String
    ): String = {
      val reqType       = methodInType(method, inEnvType)
      val scalaOutType  = method.outputType.scalaType
      val ioOutType     = s"scalapb.zio_grpc.ResponseContext[$scalaOutType]"
      val streamOutType = s"scalapb.zio_grpc.ResponseFrame[$scalaOutType]"

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType): ${io(ioOutType, outEnvType)}"
        case StreamType.ClientStreaming =>
          s"[${inEnvType}](request: $reqType): ${io(ioOutType, outEnvType + " with " + inEnvType)}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType): ${stream(streamOutType, outEnvType)}"
        case StreamType.Bidirectional   =>
          s"[${inEnvType}](request: $reqType): ${stream(streamOutType, outEnvType + " with " + inEnvType)}"
      })
    }

    def printMethodSignature(
        inEnvType: String,
        outEnvType: String
    )(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        methodSignature(
          method,
          inEnvType,
          outEnvType
        )
      )

    def printClientMethodSignature(
        inEnvType: String,
        outEnvType: String
    )(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        clientMethodSignature(
          method,
          inEnvType,
          outEnvType
        )
      )

    def printClientWithMetadataMethodSignature(
        inEnvType: String,
        outEnvType: String
    )(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        clientWithMetadataMethodSignature(
          method,
          inEnvType,
          outEnvType
        )
      )

    def printServerTransform(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val delegate = s"self.${method.name}"
      val newImpl  = method.streamType match {
        case StreamType.Unary | StreamType.ClientStreaming         =>
          s"f.effect($delegate(request))"
        case StreamType.ServerStreaming | StreamType.Bidirectional =>
          s"f.stream($delegate(request))"
      }
      fp.add(
        methodSignature(
          method,
          inEnvType = "Any",
          outEnvType = "R1 with Context1"
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
          inEnvType = "RR",
          outEnvType = "R1 with Context1"
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
          inEnvType = "RR",
          outEnvType = "R1 with Context1"
        ) + " = " + newImpl
      )
    }

    def serverTranformableService(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"implicit val transformableService: scalapb.zio_grpc.TransformableService[${ztraitName.name}] = new scalapb.zio_grpc.TransformableService[${ztraitName.name}] {"
      ).indented(
        _.add(
          s"def transform[R, Context, R1, Context1](self: ${ztraitName.name}[R, Context], f: scalapb.zio_grpc.ZTransform[R with Context, $Status, R1 with Context1]): ${ztraitName.fullName}[R1, Context1] = new ${ztraitName.fullName}[R1, Context1] {"
        ).indented(
          _.print(service.getMethods().asScala.toVector)(
            printServerTransform
          )
        ).add("}")
      ).add("}")

    def clientWithMetadataTranformableService(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"implicit val transformableService: scalapb.zio_grpc.TransformableService[ZService] = new scalapb.zio_grpc.TransformableService[ZService] {"
      ).indented(
        _.add(
          s"def transform[R, Context, R1, Context1](self: ZService[R, Context], f: scalapb.zio_grpc.ZTransform[R with Context, $Status, R1 with Context1]): ZService[R1, Context1] = new ZService[R1, Context1] {"
        ).indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithMetadataTransform
          )
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def mapCallOptionsM(cf: $CallOptions => zio.IO[$Status, $CallOptions]): ZService[R1, Context1] = transform[R, Context, R1, Context1](self.mapCallOptionsM(cf), f)",
              s"def withMetadataM(headersEffect: zio.IO[$Status, $SafeMetadata]): ZService[R1, Context1] = transform[R, Context, R1, Context1](self.withMetadataM(headersEffect), f)",
              s"def withCallOptionsM(callOptions: zio.IO[$Status, $CallOptions]): ZService[R1, Context1] = transform[R, Context, R1, Context1](self.withCallOptionsM(callOptions), f)"
            )
        ).add("}")
      ).add("}")

    def clientTranformableService(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"implicit val transformableService: scalapb.zio_grpc.TransformableService[ZService] = new scalapb.zio_grpc.TransformableService[ZService] {"
      ).indented(
        _.add(
          s"def transform[R, Context, R1, Context1](self: ZService[R, Context], f: scalapb.zio_grpc.ZTransform[R with Context, $Status, R1 with Context1]): ZService[R1, Context1] = new ZService[R1, Context1] {"
        ).indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientTransform
          )
            .add(
              "// Returns a client that gives access to headers and trailers",
              s"def withMetadata: ${clientWithMetadataServiceName.name}.ZService[R1, Context1] = self.withMetadata.transform[R1, Context1](f)"
            )
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def mapCallOptionsM(cf: $CallOptions => zio.IO[$Status, $CallOptions]): ZService[R1, Context1] = transform[R, Context, R1, Context1](self.mapCallOptionsM(cf), f)",
              s"def withMetadataM(headersEffect: zio.IO[$Status, $SafeMetadata]): ZService[R1, Context1] = transform[R, Context, R1, Context1](self.withMetadataM(headersEffect), f)",
              s"def withCallOptionsM(callOptions: zio.IO[$Status, $CallOptions]): ZService[R1, Context1] = transform[R, Context, R1, Context1](self.withCallOptionsM(callOptions), f)"
            )
        ).add("}")
      ).add("}")

    def print(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"trait ${ztraitName.name}[-R, -Context] extends scalapb.zio_grpc.ZGeneratedService[R, Context, ${ztraitName.name}] {"
      ).indented(
        _.add("self =>")
          .print(service.getMethods().asScala.toVector)(
            printMethodSignature(
              inEnvType = "Any",
              outEnvType = "R with Context"
            )
          )
      ).add("}")
        .add(
          s"type ${traitName.name} = ${ztraitName.name}[Any, Any]",
          s"type R${traitName.name}[R] = ${ztraitName.name}[R, Any]",
          s"type RC${traitName.name}[R] = ${ztraitName.name}[R, zio.Has[$RequestContext]]"
        )
        .add("")
        .add(s"object ${ztraitName.name} {")
        .indented(
          _.call(serverTranformableService)
            .add(
              s"implicit def ops[R, C](service: ${ztraitName.fullName}[R, C]): scalapb.zio_grpc.TransformableService.TransformableServiceOps[${ztraitName.fullName}, R, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[${ztraitName.fullName}, R, C](service)",
              s"implicit val genericBindable: scalapb.zio_grpc.GenericBindable[${ztraitName.fullName}] = new scalapb.zio_grpc.GenericBindable[${ztraitName.fullName}] {"
            )
            .indented(
              _.add(
                s"""def bind[R, C](serviceImpl: ${ztraitName.fullName}[R, C], env: zio.Has[$RequestContext] => R with C): zio.URIO[R, $serverServiceDef] ="""
              ).indent
                .add(s"zio.ZIO.runtime[Any].zip($CH.backpressureQueueSize).map {")
                .indent
                .add("case (runtime, queueSize) =>")
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
        .add("// accessor with metadata methods")
        .add(
          s"class ${accessorsWithMetadataClassName.name}[Context: zio.Tag](callOptions: zio.IO[$Status, $CallOptions]) extends scalapb.zio_grpc.CallOptionsMethods[${accessorsWithMetadataClassName.name}[Context]] {"
        )
        .indented(
          _.add(s"def this() = this(zio.ZIO.succeed($CallOptions.DEFAULT))")
            .print(service.getMethods().asScala.toVector)(printAccessorWithMetadata)
            .add(
              s"def mapCallOptionsM(f: $CallOptions => zio.IO[$Status, $CallOptions]) = new ${accessorsWithMetadataClassName.name}(callOptions.flatMap(f))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"type ${clientWithMetadataServiceName.name} = _root_.zio.Has[${clientWithMetadataServiceName.name}.Service]"
        )
        .add("")
        .add(
          s"object ${clientWithMetadataServiceName.name} extends ${accessorsWithMetadataClassName.name}[Any](zio.ZIO.succeed($CallOptions.DEFAULT)) {"
        )
        .indent
        .add(
          s"trait ZService[R, Context] extends scalapb.zio_grpc.CallOptionsMethods[ZService[R, Context]] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithMetadataMethodSignature(
              inEnvType = "R0",
              outEnvType = "R with Context"
            )
          )
            .add("")
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def withMetadataM(headersEffect: zio.IO[$Status, $SafeMetadata]): ZService[R, Context]",
              s"def withCallOptionsM(callOptions: zio.IO[$Status, $CallOptions]): ZService[R, Context]"
            )
        )
        .add("}")
        .add(s"type Service = ZService[Any, Any]")
        .add(s"type Accessors[Context] = ${accessorsClassName.fullName}[Context]")
        .call(clientWithMetadataTranformableService)
        .add(
          s"implicit def ops[R, C](service: ZService[R, C]): scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, R, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, R, C](service)"
        )
        .add("")
        .add("")
        .add(
          s"private[this] class ServiceStub[R, Context](channel: $ZChannel[R], options: zio.IO[$Status, $CallOptions], headers: zio.ZIO[Context, $Status, $SafeMetadata])"
        )
        .add(s"    extends ${clientWithMetadataServiceName.name}.ZService[R, Context] {")
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithMetadataImpl(envType = "R with Context")
          )
            .add(
              s"def mapCallOptionsM(f: $CallOptions => zio.IO[$Status, $CallOptions]): ZService[R, Context] = new ServiceStub(channel, options.flatMap(f), headers)",
              s"override def withMetadataM(headersEffect: zio.IO[$Status, $SafeMetadata]): ZService[R, Context] = new ServiceStub(channel, options, headersEffect)",
              s"def withCallOptionsM(callOptions: zio.IO[$Status, $CallOptions]): ZService[R, Context] = new ServiceStub(channel, callOptions, headers)"
            )
        )
        .add("}")
        .add("")
        .add(
          s"def managed[R, Context](managedChannel: $ZManagedChannel[R], options: zio.IO[$Status, $CallOptions] = zio.ZIO.succeed($CallOptions.DEFAULT), headers: zio.ZIO[Context, $Status, $SafeMetadata]=$SafeMetadata.make): zio.Managed[Throwable, ${clientWithMetadataServiceName.name}.ZService[R, Context]] = managedChannel.map {"
        )
        .add("  channel => new ServiceStub[R, Context](channel, options, headers)")
        .add("}")
        .add("")
        .add(
          s"def live[R, Context: zio.Tag](managedChannel: $ZManagedChannel[R], options: zio.IO[$Status, $CallOptions]=zio.ZIO.succeed($CallOptions.DEFAULT), headers: zio.ZIO[Context, $Status, $SafeMetadata] = $SafeMetadata.make): zio.ZLayer[R, Throwable, zio.Has[${clientWithMetadataServiceName.name}.ZService[Any, Context]]] = zio.ZLayer.fromFunctionManaged((r: R) => managed[Any, Context](managedChannel.map(_.provide(r)), options, headers))"
        )
        .outdent
        .add("}")

    def genClient(fp: FunctionalPrinter): FunctionalPrinter =
      fp
        .add("// accessor methods")
        .add(
          s"class ${accessorsClassName.name}[Context: zio.Tag](callOptions: zio.IO[$Status, $CallOptions]) extends scalapb.zio_grpc.CallOptionsMethods[${accessorsClassName.name}[Context]] {"
        )
        .indented(
          _.add(s"def this() = this(zio.ZIO.succeed($CallOptions.DEFAULT))")
            .print(service.getMethods().asScala.toVector)(printAccessor)
            .add(
              s"def mapCallOptionsM(f: $CallOptions => zio.IO[$Status, $CallOptions]) = new ${accessorsClassName.name}(callOptions.flatMap(f))"
            )
        )
        .add("}")
        .add(
          s"type ${clientServiceName.name} = _root_.zio.Has[${clientServiceName.name}.Service]"
        )
        .add("")
        .add(
          s"object ${clientServiceName.name} extends ${accessorsClassName.name}[Any](zio.ZIO.succeed($CallOptions.DEFAULT)) {"
        )
        .indent
        .add(
          s"trait ZService[R, Context] extends scalapb.zio_grpc.CallOptionsMethods[ZService[R, Context]] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientMethodSignature(
              inEnvType = "R0",
              outEnvType = "R with Context"
            )
          )
            .add("")
            .add(
              "// Returns a client that gives access to headers and trailers",
              s"def withMetadata: ${clientWithMetadataServiceName.name}.ZService[R, Context]"
            )
            .add(
              "// Returns a copy of the service with new default metadata",
              s"def withMetadataM(headersEffect: zio.IO[$Status, $SafeMetadata]): ZService[R, Context]",
              s"def withCallOptionsM(callOptions: zio.IO[$Status, $CallOptions]): ZService[R, Context]"
            )
        )
        .add("}")
        .add(s"type Service = ZService[Any, Any]")
        .add(s"type Accessors[Context] = ${accessorsClassName.fullName}[Context]")
        .call(clientTranformableService)
        .add(
          s"implicit def ops[R, C](service: ZService[R, C]): scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, R, C] = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[ZService, R, C](service)"
        )
        .add("")
        .add("")
        .add(
          s"private[this] class ServiceStub[R, Context](underlying: ${clientWithMetadataServiceName.name}.ZService[R, Context])"
        )
        .add(s"    extends ${clientServiceName.name}.ZService[R, Context] {")
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientImpl(envType = "R with Context")
          )
            .add(
              "// Returns a client that gives access to headers and trailers",
              s"def withMetadata: ${clientWithMetadataServiceName.name}.ZService[R, Context] = underlying"
            )
            .add(
              s"def mapCallOptionsM(f: $CallOptions => zio.IO[$Status, $CallOptions]): ZService[R, Context] = new ServiceStub(underlying.mapCallOptionsM(f))",
              s"override def withMetadataM(headersEffect: zio.IO[$Status, $SafeMetadata]): ZService[R, Context] = new ServiceStub(underlying.withMetadataM(headersEffect))",
              s"def withCallOptionsM(callOptions: zio.IO[$Status, $CallOptions]): ZService[R, Context] = new ServiceStub(underlying.withCallOptionsM(callOptions))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"def managed[R, Context](managedChannel: $ZManagedChannel[R], options: zio.IO[$Status, $CallOptions] = zio.ZIO.succeed($CallOptions.DEFAULT), headers: zio.ZIO[Context, $Status, $SafeMetadata]=$SafeMetadata.make): zio.Managed[Throwable, ${clientServiceName.name}.ZService[R, Context]] = ${clientWithMetadataServiceName.name}.managed(managedChannel, options, headers).map(client => new ServiceStub(client))"
        )
        .add("")
        .add(
          s"def live[R, Context: zio.Tag](managedChannel: $ZManagedChannel[R], options: zio.IO[$Status, $CallOptions]=zio.ZIO.succeed($CallOptions.DEFAULT), headers: zio.ZIO[Context, $Status, $SafeMetadata] = $SafeMetadata.make): zio.ZLayer[R, Throwable, zio.Has[${clientServiceName.name}.ZService[Any, Context]]] = ${clientWithMetadataServiceName.name}.live(managedChannel, options, headers).map(clientHas => _root_.zio.Has(new ServiceStub(clientHas.get)))"
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
          inEnvType = "R0",
          outEnvType = s"zio.Has[${clientServiceName.name}.ZService[Any, Context]] with Context"
        ) + " = "
      val innerCall         = s"_.get.withCallOptionsM(callOptions).${method.name}(request)"
      val clientCall        = method.streamType match {
        case StreamType.Unary           => s"_root_.zio.ZIO.accessM($innerCall)"
        case StreamType.ClientStreaming => s"_root_.zio.ZIO.accessM($innerCall)"
        case StreamType.ServerStreaming =>
          s"_root_.zio.stream.ZStream.accessStream($innerCall)"
        case StreamType.Bidirectional   =>
          s"_root_.zio.stream.ZStream.accessStream($innerCall)"
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
          inEnvType = "R0",
          outEnvType = s"zio.Has[${clientWithMetadataServiceName.name}.ZService[Any, Context]] with Context"
        ) + " = "
      val innerCall         = s"_.get.withCallOptionsM(callOptions).${method.name}(request)"
      val clientCall        = method.streamType match {
        case StreamType.Unary           =>
          s"_root_.zio.ZIO.accessM($innerCall)"
        case StreamType.ClientStreaming =>
          s"_root_.zio.ZIO.accessM($innerCall)"
        case StreamType.ServerStreaming =>
          s"_root_.zio.stream.ZStream.accessStream($innerCall)"
        case StreamType.Bidirectional   =>
          s"_root_.zio.stream.ZStream.accessStream($innerCall)"
      }
      fp.add(sigWithoutContext + clientCall)
    }

    def printClientWithMetadataImpl(
        envType: String
    )(fp: FunctionalPrinter, method: MethodDescriptor): FunctionalPrinter = {
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
          s"zio.stream.ZStream.fromEffect(headers.zip(options)).flatMap"
        case StreamType.Bidirectional   =>
          s"zio.stream.ZStream.fromEffect(headers.zip(options)).flatMap"
      }
      fp.add(
        clientWithMetadataMethodSignature(
          method,
          inEnvType = "R0",
          outEnvType = envType
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
          inEnvType = "R0",
          outEnvType = envType
        ) + s" = underlying.${method.name}(request)$suffix"
      )
    }

    def printBindService(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
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
          s"$CH.$serverCall(runtime, queueSize, (t: ${methodInType(method, inEnvType = "Any")})=>serviceImpl.${method.name}(t).provideSome(env))"
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
