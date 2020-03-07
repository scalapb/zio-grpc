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

object ZioCodeGenerator extends CodeGenApp {
  override def registerExtensions(registry: ExtensionRegistry): Unit =
    Scalapb.registerAllExtensions(registry)

  override def suggestedDependencies: Seq[Artifact] = Seq(
    Artifact(
      "com.thesamet.scalapb.zio-grpc",
      "zio-grpc-core",
      BuildInfo.version,
      crossVersion = true
    )
  )

  def process(request: CodeGenRequest): CodeGenResponse = {
    ProtobufGenerator.parseParameters(request.parameter) match {
      case Right(params) =>
        val implicits =
          new DescriptorImplicits(params, request.allProtos)
        CodeGenResponse.succeed(
          request.filesToGenerate
            .collect {
              case file if !file.getServices().isEmpty() =>
                new ZioFilePrinter(implicits, file).result()
            }
        )
      case Left(error) =>
        CodeGenResponse.fail(error)
    }
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
  private val OuterObject = file.scalaPackage / s"Zio${file.javaOuterClassName}"

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
    private val clientServiceName = OuterObject / (service.name + "Client")

    def methodSignature(method: MethodDescriptor, envType: String): String = {
      val scalaInType = method.inputType.scalaType
      val scalaOutType = method.outputType.scalaType

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary =>
          s"(request: $scalaInType): ${io(scalaOutType, envType)}"
        case StreamType.ClientStreaming =>
          s"(request: ${stream(scalaInType, "Any")}): ${io(scalaOutType, envType)}"
        case StreamType.ServerStreaming =>
          s"(request: $scalaInType): ${stream(scalaOutType, envType)}"
        case StreamType.Bidirectional =>
          s"(request: ${stream(scalaInType, "Any")}): ${stream(scalaOutType, envType)}"
      })
    }

    def printMethodSignature(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      fp.add(methodSignature(method, envType = "Any"))
    }

    def print(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(s"trait ${traitName.name} {")
        .indent
        .print(service.getMethods().asScala.toVector)(
          printMethodSignature
        )
        .outdent
        .add("}")
        .add("")
        .add(
          s"type ${clientServiceName.name} = _root_.zio.Has[${clientServiceName.name}.Service]"
        )
        .add(s"object ${clientServiceName.name} {")
        .indent
        .add(s"trait Service extends ${traitName.fullName}")
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
        .add(
          s"def live(managedChannel: $ZManagedChannel, options: $CallOptions = $CallOptions.DEFAULT, headers: => $Metadata = new $Metadata()): zio.ZLayer.NoDeps[Throwable, ${clientServiceName.name}] = zio.ZLayer.fromManaged(managed(managedChannel, options, headers))"
        )
        .outdent
        .add("}")
        .add("")
        .add("")
        .add(
          s"implicit def bindableService: $ZBindableService[${traitName.name}] = new $ZBindableService[${traitName.name}] {"
        )
        .indent
        .add(
          s"""def bindService(serviceImpl: ${traitName.name}): zio.UIO[$serverServiceDef] ="""
        )
        .indent
        .add("zio.ZIO.runtime[Any].map {")
        .indent
        .add("runtime: zio.Runtime[Any] =>")
        .indent
        .add(
          s"""$serverServiceDef.builder(${service.grpcDescriptor.fullName})"""
        )
        .print(service.getMethods().asScala.toVector)(printBindService)
        .add(".build()")
        .outdent
        .outdent
        .add("}")
        .outdent
        .outdent
        .add("}")

    def printAccessor(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val sig =
        methodSignature(method, envType = clientServiceName.name) + " = "
      val innerCall = s"_.get.${method.name}(request)"
      val clientCall = method.streamType match {
        case StreamType.Unary           => s"_root_.zio.ZIO.accessM($innerCall)"
        case StreamType.ClientStreaming => s"_root_.zio.ZIO.accessM($innerCall)"
        case StreamType.ServerStreaming =>
          s"_root_.zio.stream.ZStream.accessStream($innerCall)"
        case StreamType.Bidirectional =>
          s"_root_.zio.stream.ZStream.accessStream($innerCall)"
      }
      fp.add(sig + clientCall)
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
      fp.add(methodSignature(method, envType) + s" = $clientCall(")
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
        method: MethodDescriptor
    ): FunctionalPrinter = {
      val CH = "_root_.scalapb.zio_grpc.server.ZServerCallHandler"

      val serverCall = method.streamType match {
        case StreamType.Unary           => "unaryCallHandler"
        case StreamType.ClientStreaming => "clientStreamingCallHandler"
        case StreamType.ServerStreaming => "serverStreamingCallHandler"
        case StreamType.Bidirectional   => "bidiCallHandler"
      }

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

  def stream(res: String, envType: String) = envType match {
    case "Any" => s"_root_.zio.stream.Stream[io.grpc.Status, $res]"
    case r     => s"_root_.zio.stream.ZStream[$r, io.grpc.Status, $res]"
  }

  def io(res: String, envType: String) = envType match {
    case "Any" => s"_root_.zio.IO[io.grpc.Status, $res]"
    case r     => s"_root_.zio.ZIO[$r, io.grpc.Status, $res]"
  }
}
