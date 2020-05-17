package scalapb.zio_grpc

import com.google.protobuf.ExtensionRegistry
import scalapb.options.compiler.Scalapb
import scalapb.compiler.ProtobufGenerator
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.Artifact
import com.google.protobuf.Descriptors.FileDescriptor
import scalapb.zio_grpc.compat.JavaConverters._
import scalapb.compiler.DescriptorImplicits
import com.google.protobuf.Descriptors.ServiceDescriptor
import com.google.protobuf.Descriptors.MethodDescriptor
import scalapb.compiler.StreamType
import scalapb.compiler.FunctionalPrinter
import protocbridge.codegen.CodeGenApp
import protocbridge.codegen.CodeGenResponse
import protocbridge.codegen.CodeGenRequest
import scalapb.compiler.NameUtils

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
          new DescriptorImplicits(params, request.allProtos)
        CodeGenResponse.succeed(
          request.filesToGenerate.collect {
            case file if !file.getServices().isEmpty() =>
              new ZioFilePrinter(implicits, file).result()
          }
        )
      case Left(error) =>
        CodeGenResponse.fail(error)
    }
}

class ZioFilePrinter(
    implicits: DescriptorImplicits,
    file: FileDescriptor
) {
  import implicits._

  val Channel = "io.grpc.Channel"
  val CallOptions = "io.grpc.CallOptions"
  val ClientCalls = "scalapb.zio_grpc.client.ClientCalls"
  val Metadata = "io.grpc.Metadata"
  val ZClientCall = "scalapb.zio_grpc.client.ZClientCall"
  val ZManagedChannel = "scalapb.zio_grpc.ZManagedChannel"
  val ZBindableService = "scalapb.zio_grpc.ZBindableService"
  val serverServiceDef = "_root_.io.grpc.ServerServiceDefinition"
  private val OuterObject =
    file.scalaPackage / s"Zio${NameUtils.snakeCaseToCamelCase(baseName(file.getName), true)}"

  def scalaFileName =
    OuterObject.fullName.replace('.', '/') + ".scala"

  def content: String = {
    val fp = new FunctionalPrinter()
    fp.add(s"package ${file.scalaPackage.fullName}", "")
      .add()
      .add(s"object ${OuterObject.name} {")
      .indent
      .print(file.getServices().asScala)((fp, s) =>
        new ServicePrinter(s).print(fp)
      )
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

    private val traitName = OuterObject / service.name
    private val ztraitName = OuterObject / ("Z" + service.name)
    private val withContext = traitName / "WithContext"
    private val withMetadata = traitName / "WithMetadata"

    private val clientServiceName = OuterObject / (service.name + "Client")

    def methodSignature(
        method: MethodDescriptor,
        inEnvType: String,
        outEnvType: String,
        contextType: Option[String]
    ): String = {
      val scalaInType = method.inputType.scalaType
      val scalaOutType = method.outputType.scalaType
      val maybeContext = contextType.fold("")(ct => s", context: ${ct}")

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary =>
          s"(request: $scalaInType$maybeContext): ${io(scalaOutType, outEnvType)}"
        case StreamType.ClientStreaming =>
          s"(request: ${stream(scalaInType, inEnvType)}$maybeContext): ${io(scalaOutType, outEnvType)}"
        case StreamType.ServerStreaming =>
          s"(request: $scalaInType$maybeContext): ${stream(scalaOutType, outEnvType)}"
        case StreamType.Bidirectional =>
          s"(request: ${stream(scalaInType, inEnvType)}$maybeContext): ${stream(scalaOutType, outEnvType)}"
      })
    }

    def printMethodSignature(
        inEnvType: String = "Any",
        outEnvType: String = "Any",
        contextType: Option[String]
    )(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        methodSignature(
          method,
          inEnvType,
          outEnvType,
          contextType = contextType
        )
      )

    def printAsEnv(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val delegate = s"serviceImpl.${method.name}"
      val newImpl = method.streamType match {
        case StreamType.Unary | StreamType.ClientStreaming =>
          s"zio.ZIO.accessM[zio.Has[Context]](context => $delegate(request, context.get))"
        case StreamType.ServerStreaming | StreamType.Bidirectional =>
          s"zio.stream.ZStream.accessStream[zio.Has[Context]](context => $delegate(request, context.get))"
      }
      fp.add(
        methodSignature(
          method,
          inEnvType = "Any",
          outEnvType = "zio.Has[Context]",
          contextType = None
        ) + " = " + newImpl
      )
    }

    def printWithAnyContext(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        methodSignature(
          method,
          inEnvType = "Any",
          outEnvType = "Any",
          contextType = Some("Any")
        ) + s" = serviceImpl.${method.name}(request)"
      )

    def printTransformContext(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val delegate = s"serviceImpl.${method.name}"
      val newImpl = method.streamType match {
        case StreamType.Unary | StreamType.ClientStreaming =>
          s"f(context).flatMap($delegate(request, _))"
        case StreamType.ServerStreaming | StreamType.Bidirectional =>
          s"_root_.zio.stream.ZStream.fromEffect(f(context)).flatMap($delegate(request, _))"
      }
      fp.add(
        methodSignature(
          method,
          inEnvType = "Any",
          outEnvType = "Any",
          contextType = Some("NewContext")
        ) + " = " + newImpl
      )
    }

    def printTransform(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val delegate = s"serviceImpl.${method.name}"
      val newImpl = method.streamType match {
        case StreamType.Unary | StreamType.ClientStreaming =>
          s"f.effect($delegate(request))"
        case StreamType.ServerStreaming | StreamType.Bidirectional =>
          s"f.stream($delegate(request))"
      }
      fp.add(
        methodSignature(
          method,
          inEnvType = "Any",
          outEnvType = "R1 with Context1",
          contextType = None
        ) + " = " + newImpl
      )
    }

    def print(fp: FunctionalPrinter): FunctionalPrinter = {
      fp.add(s"trait ${ztraitName.name}[-R, -Context] {")
        .indent
        .print(service.getMethods().asScala.toVector)(
          printMethodSignature(
            inEnvType = "Any",
            outEnvType = "R with Context",
            contextType = None
          )
        )
        .outdent
        .add("}")
        .add(s"type ${traitName.name} = ${ztraitName.name}[Any, Any]")
        .add("")
        .add(s"object ${ztraitName.name} {")
        .indented(
          _.add(
            s"def transform[R, Context, R1, Context1](serviceImpl: ${ztraitName.name}[R, Context], f: scalapb.zio_grpc.ZTransform[R with Context, io.grpc.Status, R1 with Context1]): ${ztraitName.fullName}[R1, Context1] = new ${ztraitName.fullName}[R1, Context1] {"
          ).indented(
              _.print(service.getMethods().asScala.toVector)(
                printTransform
              )
            )
            .add("}")
            .add("")
            .add(
              s"def provide[R <: zio.Has[_], Context <: zio.Has[_] : zio.Tagged](serviceImpl: ${ztraitName.name}[R, Context], env: R): ${ztraitName.fullName}[Any, Context] ="
            )
            .add(
              "  transform(serviceImpl, scalapb.zio_grpc.ZTransform.provideEnv[R, io.grpc.Status, Context](env))"
            )
            .add(
              s"def transformContext[R <: zio.Has[_], C1: zio.Tagged, C2: zio.Tagged](serviceImpl: ${ztraitName.name}[R, zio.Has[C1]], f: C2 => ${io("C1", "R")}): ${ztraitName.fullName}[R, zio.Has[C2]] ="
            )
            .add(
              "  transform(serviceImpl, scalapb.zio_grpc.ZTransform.transformContext[R, io.grpc.Status, zio.Has[C1], zio.Has[C2]]((hc2: zio.Has[C2]) => f(hc2.get).map(zio.Has(_))))"
            )
            .add(
              s"def toLayer[R <: zio.Has[_], Context <: zio.Has[_] : zio.Tagged](serviceImpl: ${ztraitName.name}[R, Context]): zio.ZLayer[R, Nothing, zio.Has[${ztraitName.fullName}[Any, Context]]] ="
            )
            .add("  zio.ZLayer.fromFunction(provide(serviceImpl, _))")
        )
        .add("}")
        .add("")
        .add(s"object ${traitName.name} {")
        .indented(
          _.add(s"trait ${withContext.name}[-Context] {")
            .indented(
              _.print(service.getMethods().asScala.toVector)(
                printMethodSignature(contextType = Some("Context"))
              )
            )
            .add("}")
            .add(
              s"type ${withMetadata.name} = ${withContext.name}[${Metadata}]"
            )
            .add("")
            .add(
              s"def asEnv[Context : zio.Tagged](serviceImpl: ${withContext.name}[Context]): ${ztraitName.fullName}[Any, zio.Has[Context]] = new ${ztraitName.fullName}[Any, zio.Has[Context]] {"
            )
            .indented(
              _.print(service.getMethods().asScala.toVector)(
                printAsEnv
              )
            )
            .add("}")
            .add("")
            .add(
              s"def transformContext[Context, NewContext](serviceImpl: ${withContext.name}[Context], f: NewContext => ${io("Context", "Any")}): ${withContext.name}[NewContext] = new ${withContext.name}[NewContext] {"
            )
            .indented(
              _.print(service.getMethods().asScala.toVector)(
                printTransformContext
              )
            )
            .add("}")
            .add(
              s"def withAnyContext(serviceImpl: ${traitName.name}): ${withContext.name}[Any] = new ${withContext.name}[Any] {"
            )
            .indented(
              _.print(service.getMethods().asScala.toVector)(
                printWithAnyContext
              )
            )
            .add("}")
            .add(
              s"def transformContext[NewContext](serviceImpl: ${traitName.name}, f: NewContext => ${io("Unit", "Any")}): ${withContext.name}[NewContext] = transformContext(withAnyContext(serviceImpl), f)"
            )
        )
        .add("}")
        .add("")
        .add(
          s"type ${clientServiceName.name} = _root_.zio.Has[${clientServiceName.name}.Service]"
        )
        .add("")
        .add(s"object ${clientServiceName.name} {")
        .indent
        .add(
          s"trait Service extends ${traitName.fullName}"
        )
        .add("")
        .add("// accessor methods")
        .print(service.getMethods().asScala.toVector)(printAccessor)
        .add("")
        .add(
          s"def managed(managedChannel: $ZManagedChannel, options: $CallOptions = $CallOptions.DEFAULT, headers: => $Metadata = new $Metadata()): zio.Managed[Throwable, ${clientServiceName.name}.Service] = managedChannel.map {"
        )
        .indent
        .add("channel => new Service {")
        .indent
        .print(service.getMethods().asScala.toVector)(
          printClientImpl(envType = "Any")
        )
        .outdent
        .add("}")
        .outdent
        .add("}")
        .add("")
        .add(
          s"def live(managedChannel: $ZManagedChannel, options: $CallOptions = $CallOptions.DEFAULT, headers: => $Metadata = new $Metadata()): zio.Layer[Throwable, ${clientServiceName.name}] = zio.ZLayer.fromManaged(managed(managedChannel, options, headers))"
        )
        .outdent
        .add("}")
        .add("")
        .add(
          s"implicit def bindableServiceWithMetadata: $ZBindableService.Aux[${withMetadata.fullName}, Any] = new $ZBindableService[${withMetadata.fullName}] {"
        )
        .indent
        .add("type R = Any")
        .add(
          s"""def bindService(serviceImpl: ${withMetadata.fullName}): zio.UIO[$serverServiceDef] ="""
        )
        .add(
          s"  bindableServiceWithMetadataAsEnv.bindService(${traitName.fullName}.asEnv(serviceImpl))"
        )
        .outdent
        .add("}")
        .add(
          s"implicit def bindableServiceWithMetadataAsEnv: $ZBindableService.Aux[${ztraitName.fullName}[Any, zio.Has[$Metadata]], Any] = new $ZBindableService[${ztraitName.fullName}[Any, zio.Has[$Metadata]]] {"
        )
        .indent
        .add("type R = Any")
        .add(
          s"""def bindService(serviceImpl: ${ztraitName.fullName}[Any, zio.Has[$Metadata]]): zio.URIO[Any, $serverServiceDef] ="""
        )
        .indent
        .add("zio.ZIO.runtime[Any].map {")
        .indent
        .add("runtime =>")
        .indent
        .add(
          s"""$serverServiceDef.builder(${service.grpcDescriptor.fullName})"""
        )
        .print(service.getMethods().asScala.toVector)(
          printBindService(_, _, suffix = "R")
        )
        .add(".build()")
        .outdent
        .outdent
        .add("}")
        .outdent
        .outdent
        .add("}")
        .add(
          s"implicit def bindableServiceWithMetadataAndR[R0 <: zio.Has[_]]: $ZBindableService.Aux[${ztraitName.fullName}[R0, zio.Has[$Metadata]], R0] = new $ZBindableService[${ztraitName.fullName}[R0, zio.Has[$Metadata]]] {"
        )
        .indent
        .add("type R = R0")
        .add(
          s"""def bindService(serviceImpl0: ${ztraitName.fullName}[R, zio.Has[$Metadata]]): zio.URIO[R, $serverServiceDef] ="""
        )
        .indent
        .add(
          s"zio.ZIO.environment[R0].flatMap((env: R0) => bindableServiceWithMetadataAsEnv.bindService(${ztraitName.fullName}.provide(serviceImpl0, env)))"
        )
        .outdent
        .add("}")
    }

    def printAccessor(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val sigWithoutContext =
        methodSignature(
          method,
          inEnvType = "Any",
          outEnvType = clientServiceName.name,
          contextType = None
        ) + " = "
      val innerCall = s"_.get.${method.name}(request)"
      val clientCall = method.streamType match {
        case StreamType.Unary           => s"_root_.zio.ZIO.accessM($innerCall)"
        case StreamType.ClientStreaming => s"_root_.zio.ZIO.accessM($innerCall)"
        case StreamType.ServerStreaming =>
          s"_root_.zio.stream.ZStream.accessStream($innerCall)"
        case StreamType.Bidirectional =>
          s"_root_.zio.stream.ZStream.accessStream($innerCall)"
      }
      fp.add(sigWithoutContext + clientCall)
    }

    def printClientImpl(
        envType: String
    )(fp: FunctionalPrinter, method: MethodDescriptor): FunctionalPrinter = {
      val clientCall = method.streamType match {
        case StreamType.Unary           => s"$ClientCalls.unaryCall"
        case StreamType.ClientStreaming => s"$ClientCalls.clientStreamingCall"
        case StreamType.ServerStreaming => s"$ClientCalls.serverStreamingCall"
        case StreamType.Bidirectional   => s"$ClientCalls.bidiCall"
      }
      fp.add(
          methodSignature(
            method,
            inEnvType = "Any",
            outEnvType = envType,
            contextType = None
          ) + s" = $clientCall("
        )
        .indent
        .add(
          s"$ZClientCall(channel.newCall(${method.grpcDescriptor.fullName}, options)),"
        )
        .add(s"headers,")
        .add(s"request")
        .outdent
        .add(s")")
    }
    def printBindService(
        fp: FunctionalPrinter,
        method: MethodDescriptor,
        suffix: String = ""
    ): FunctionalPrinter = {
      val CH = "_root_.scalapb.zio_grpc.server.ZServerCallHandler"

      val serverCall = (method.streamType match {
        case StreamType.Unary           => "unaryCallHandler"
        case StreamType.ClientStreaming => "clientStreamingCallHandler"
        case StreamType.ServerStreaming => "serverStreamingCallHandler"
        case StreamType.Bidirectional   => "bidiCallHandler"
      }) + suffix

      fp.add(".addMethod(")
        .indent
        .add(
          s"${method.grpcDescriptor.fullName},"
        )
        .add(s"$CH.$serverCall(runtime, serviceImpl.${method.name})")
        .outdent
        .add(")")
    }
  }

  def stream(res: String, envType: String) =
    envType match {
      case "Any" => s"_root_.zio.stream.Stream[io.grpc.Status, $res]"
      case r     => s"_root_.zio.stream.ZStream[$r, io.grpc.Status, $res]"
    }

  def io(res: String, envType: String) =
    envType match {
      case "Any" => s"_root_.zio.IO[io.grpc.Status, $res]"
      case r     => s"_root_.zio.ZIO[$r, io.grpc.Status, $res]"
    }
}
