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
            .flatMap(file => generateServices(file, implicits))
        )
      case Left(error) =>
        CodeGenResponse.fail(error)
    }
  }

  def generateServices(
      file: FileDescriptor,
      implicits: DescriptorImplicits
  ): Seq[CodeGeneratorResponse.File] = {
    import implicits._
    file.getServices().asScala.toVector.map { s =>
      val b = CodeGeneratorResponse.File.newBuilder()
      val printer = new ZioServicePrinter(implicits, s)
      b.setName(file.scalaDirectory + s"/${printer.ModuleName}.scala")
      b.setContent(printer.printService(FunctionalPrinter()).result())
      b.build()
    }
  }
}

class ZioServicePrinter(
    implicits: DescriptorImplicits,
    service: ServiceDescriptor
) {
  import implicits._

  val ModuleName = s"${service.name}"

  val valueName = s"${service.name(0).toLower}${service.name.tail}"
  val servicePackageName: String = service.getFile.scalaPackage.fullName

  val Channel = "io.grpc.Channel"
  val CallOptions = "io.grpc.CallOptions"
  private val PackageObjectName = service.getFile.scalaPackage / valueName
  val ClientCalls = "scalapb.zio_grpc.client.ClientCalls"
  val Metadata = "io.grpc.Metadata"
  val ZClientCall = "scalapb.zio_grpc.client.ZClientCall"
  val serverServiceDef = "_root_.io.grpc.ServerServiceDefinition"

  def printService(fp: FunctionalPrinter): FunctionalPrinter = {
    fp.add(s"package ${servicePackageName}", "")
      .add("")
      .add(s"package object ${PackageObjectName.nameSymbol} {")
      .indent
      .add(s"type $ModuleName = zio.Has[$ModuleName.Service[Any]]")
      .add("")
      .add(
        s"object $ModuleName extends _root_.scalapb.zio_grpc.ServiceModule {"
      )
      .indent
      .add(s"trait Service[R] { self =>")
      .indent
      .print(service.getMethods().asScala.toVector)(
        printMethodSignature(envType = "R")
      )
      .add("def provide_(env: R): Service[Any] = new Service[Any] {")
      .indent
      .print(service.getMethods().asScala.toVector)(
        printMethodProvide
      )
      .outdent
      .add("}")
      .outdent
      .add(s"}")
      .add("")
      .add(
        "implicit def bindableService[R]: _root_.scalapb.zio_grpc.ZBindableService[R, Service[R]] = new _root_.scalapb.zio_grpc.ZBindableService[R, Service[R]] {"
      )
      .indent
      .add(
        s"""def bindService(serviceImpl: ${ModuleName}.Service[R]): zio.URIO[R, $serverServiceDef] ="""
      )
      .indent
      .add("zio.ZIO.runtime[R].map {")
      .indent
      .add("runtime: zio.Runtime[R] =>")
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
      .add("")
      .add(
        s"final class Client(channel: $Channel, options: $CallOptions, headers: => $Metadata) extends ${ModuleName}.Service[Any] {"
      )
      .indent
      .print(service.getMethods().asScala.toVector)(
        printClientImpl(envType = "Any")
      )
      .outdent
      .add("}")
      .add("")
      .add(
        s"def client(channel: $Channel, options: $CallOptions = $CallOptions.DEFAULT, headers: => $Metadata = new $Metadata()): ${ModuleName}.Service[Any] = new Client(channel, options, headers)"
      )
      .add("")
      .add(
        s"def clientService(channel: $Channel, options: $CallOptions = $CallOptions.DEFAULT, headers: => $Metadata = new $Metadata()): ${ModuleName} = _root_.zio.Has("
      )
      .indent
      .add(
        s"client(channel, options, headers)"
      )
      .outdent
      .add(s")")
      .add("")
      .print(service.getMethods().asScala.toVector)(printAccessor)
      .outdent
      .add(s"}")
      .outdent
      .add("}")
  }

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
      envType: String
  )(fp: FunctionalPrinter, method: MethodDescriptor): FunctionalPrinter = {
    fp.add(methodSignature(method, envType))
  }

  def printMethodProvide(
      fp: FunctionalPrinter,
      method: MethodDescriptor
  ): FunctionalPrinter = {
    fp.add(
      methodSignature(method, "Any") + s" = self.${method.name}(request).provide(env)"
    )
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

  def printAccessor(
      fp: FunctionalPrinter,
      method: MethodDescriptor
  ): FunctionalPrinter = {
    val sig = methodSignature(method, envType = ModuleName) + " = "
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

  def stream(res: String, envType: String) =
    s"_root_.zio.stream.ZStream[$envType, io.grpc.Status, $res]"

  def io(res: String, envType: String) =
    s"_root_.zio.ZIO[$envType, io.grpc.Status, $res]"
}
