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
import protocgen.CodeGenApp
import protocgen.CodeGenResponse
import protocgen.CodeGenRequest
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
  val SafeMetadata        = "scalapb.zio_grpc.SafeMetadata"
  val Status              = "io.grpc.Status"
  val methodDescriptor    = "io.grpc.MethodDescriptor"
  val RequestContext      = "scalapb.zio_grpc.RequestContext"
  val ZClientCall         = "scalapb.zio_grpc.client.ZClientCall"
  val ZManagedChannel     = "scalapb.zio_grpc.ZManagedChannel"
  val ZBindableService    = "scalapb.zio_grpc.ZBindableService"
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

    private val clientServiceName = OuterObject / (service.name + "Client")

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

    def printTransform(
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

    def print(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"trait ${ztraitName.name}[R, Context] extends scalapb.zio_grpc.ZGeneratedService[R, Context, ${ztraitName.name}] {"
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
          _.add(
            s"implicit val transformableService: scalapb.zio_grpc.TransformableService[${ztraitName.name}] = new scalapb.zio_grpc.TransformableService[${ztraitName.name}] {"
          ).indented(
            _.add(
              s"def transform[R, Context, R1, Context1](self: ${ztraitName.name}[R, Context], f: scalapb.zio_grpc.ZTransform[R with Context, $Status, R1 with Context1]): ${ztraitName.fullName}[R1, Context1] = new ${ztraitName.fullName}[R1, Context1] {"
            ).indented(
              _.print(service.getMethods().asScala.toVector)(
                printTransform
              )
            ).add("}")
          ).add("}")
            .add(
              s"implicit def ops[R, C](service: ${ztraitName.fullName}[R, C]) = new scalapb.zio_grpc.TransformableService.TransformableServiceOps[${ztraitName.fullName}, R, C](service)",
              s"implicit val genericBindable: scalapb.zio_grpc.GenericBindable[${ztraitName.fullName}] = new scalapb.zio_grpc.GenericBindable[${ztraitName.fullName}] {"
            )
            .indented(
              _.add(
                s"""def bind[R, C](serviceImpl: ${ztraitName.fullName}[R, C], env: zio.Has[$RequestContext] => R with C): zio.URIO[R, $serverServiceDef] ="""
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
        .add(
          s"type ${clientServiceName.name} = _root_.zio.Has[${clientServiceName.name}.Service]"
        )
        .add("")
        .add(s"object ${clientServiceName.name} {")
        .indent
        .add(
          s"trait ZService[R] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientMethodSignature(
              inEnvType = "R0",
              outEnvType = "R"
            )
          )
        )
        .add("}")
        .add(
          s"type Service = ZService[Any]"
        )
        .add("")
        .add("// accessor methods")
        .print(service.getMethods().asScala.toVector)(printAccessor)
        .add("")
        .add(
          s"def managed[R](managedChannel: $ZManagedChannel[R], options: $CallOptions = $CallOptions.DEFAULT, headers: zio.UIO[$SafeMetadata] = $SafeMetadata.make): zio.Managed[Throwable, ${clientServiceName.name}.ZService[R]] = managedChannel.map {"
        )
        .indent
        .add("channel => new ZService[R] {")
        .indent
        .print(service.getMethods().asScala.toVector)(
          printClientImpl(envType = "R")
        )
        .outdent
        .add("}")
        .outdent
        .add("}")
        .add("")
        .add(
          s"def live[R](managedChannel: $ZManagedChannel[R], options: $CallOptions = $CallOptions.DEFAULT, headers: zio.UIO[$SafeMetadata] = $SafeMetadata.make): zio.ZLayer[R, Throwable, ${clientServiceName.name}] = zio.ZLayer.fromFunctionManaged((r: R) => managed(managedChannel.map(_.provide(r)), options, headers))"
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
          outEnvType = clientServiceName.name
        ) + " = "
      val innerCall         = s"_.get.${method.name}(request)"
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

    def printClientImpl(
        envType: String
    )(fp: FunctionalPrinter, method: MethodDescriptor): FunctionalPrinter = {
      val clientCall = method.streamType match {
        case StreamType.Unary           => s"$ClientCalls.unaryCall"
        case StreamType.ClientStreaming => s"$ClientCalls.clientStreamingCall"
        case StreamType.ServerStreaming => s"$ClientCalls.serverStreamingCall"
        case StreamType.Bidirectional   => s"$ClientCalls.bidiCall"
      }
      val prefix     = method.streamType match {
        case StreamType.Unary           => s"headers.flatMap"
        case StreamType.ClientStreaming => s"headers.flatMap"
        case StreamType.ServerStreaming =>
          s"zio.stream.ZStream.fromEffect(headers).flatMap"
        case StreamType.Bidirectional   =>
          s"zio.stream.ZStream.fromEffect(headers).flatMap"
      }
      fp.add(
        clientMethodSignature(
          method,
          inEnvType = "R0",
          outEnvType = envType
        ) + s" = $prefix { headers => $clientCall("
      ).indent
        .add(
          s"channel, ${method.grpcDescriptor.fullName}, options,"
        )
        .add(s"headers,")
        .add(s"request")
        .outdent
        .add(s")}")
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
          s"$CH.$serverCall(runtime, (t: ${methodInType(method, inEnvType = "Any")})=>serviceImpl.${method.name}(t).provideSome(env))"
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
