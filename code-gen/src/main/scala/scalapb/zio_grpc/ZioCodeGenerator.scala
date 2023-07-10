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
  val StatusException     = "io.grpc.StatusException"
  val Deadline            = "io.grpc.Deadline"
  val methodDescriptor    = "io.grpc.MethodDescriptor"
  val RequestContext      = "scalapb.zio_grpc.RequestContext"
  val CallContext         = "scalapb.zio_grpc.CallContext"
  val ZClientCall         = "scalapb.zio_grpc.client.ZClientCall"
  val ZManagedChannel     = "scalapb.zio_grpc.ZManagedChannel"
  val ZChannel            = "scalapb.zio_grpc.ZChannel"
  val GTransform          = "scalapb.zio_grpc.GTransform"
  val ZTransform          = "scalapb.zio_grpc.ZTransform"
  val Transform           = "scalapb.zio_grpc.Transform"
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
    private val gtraitName = OuterObject / ("G" + service.name)

    private val clientServiceName                      = OuterObject / (service.name + "Client")
    private val clientWithResponseMetadataServiceName  = OuterObject / (service.name + "ClientWithResponseMetadata")
    private val accessorsClassName                     = OuterObject / (service.name + "Accessors")
    private val accessorsWithResponseMetadataClassName = OuterObject / (service.name + "WithResponseMetadataAccessors")

    def methodInType(method: MethodDescriptor, errorType: String): String = {
      val scalaInType = method.inputType.scalaType

      method.streamType match {
        case StreamType.Unary           =>
          scalaInType
        case StreamType.ClientStreaming =>
          stream(scalaInType, errorType, "Any")
        case StreamType.ServerStreaming =>
          scalaInType
        case StreamType.Bidirectional   =>
          stream(scalaInType, errorType, "Any")
      }
    }

    def methodSignature(
        method: MethodDescriptor,
        contextType: Option[String],
        errorType: Option[String]
    ): String = {
      val scalaOutType = method.outputType.scalaType
      val contextParam = contextType.fold("")(ctx => s""", context: $ctx""")
      val errorParam   = errorType.getOrElse("Error")
      val reqType      = methodInType(method, StatusException)

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType$contextParam): ${io(scalaOutType, errorParam, "Any")}"
        case StreamType.ClientStreaming =>
          s"(request: $reqType$contextParam): ${io(scalaOutType, errorParam, "Any")}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType$contextParam): ${stream(scalaOutType, errorParam, "Any")}"
        case StreamType.Bidirectional   =>
          s"(request: $reqType$contextParam): ${stream(scalaOutType, errorParam, "Any")}"
      })
    }

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

    def clientMethodSignature(
        method: MethodDescriptor,
        contextType: String
    ): String = {
      val reqType      = methodInType(method, StatusException)
      val scalaOutType = method.outputType.scalaType

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType): ${io(scalaOutType, StatusException, contextType)}"
        case StreamType.ClientStreaming =>
          s"(request: $reqType): ${io(scalaOutType, StatusException, contextType)}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType): ${stream(scalaOutType, StatusException, contextType)}"
        case StreamType.Bidirectional   =>
          s"(request: $reqType): ${stream(scalaOutType, StatusException, contextType)}"
      })
    }

    def clientWithResponseMetadataSignature(
        method: MethodDescriptor,
        envOutType: String
    ): String = {
      val reqType       = methodInType(method, StatusException)
      val scalaOutType  = method.outputType.scalaType
      val ioOutType     = s"scalapb.zio_grpc.ResponseContext[$scalaOutType]"
      val streamOutType = s"scalapb.zio_grpc.ResponseFrame[$scalaOutType]"

      s"def ${method.name}" + (method.streamType match {
        case StreamType.Unary           =>
          s"(request: $reqType): ${io(ioOutType, StatusException, envOutType)}"
        case StreamType.ClientStreaming =>
          s"(request: $reqType): ${io(ioOutType, StatusException, envOutType)}"
        case StreamType.ServerStreaming =>
          s"(request: $reqType): ${stream(streamOutType, StatusException, envOutType)}"
        case StreamType.Bidirectional   =>
          s"(request: $reqType): ${stream(streamOutType, StatusException, envOutType)}"
      })
    }

    def printMethodSignature(
        contextType: Option[String],
        errorType: Option[String]
    )(
        fp: FunctionalPrinter,
        method: MethodDescriptor
    ): FunctionalPrinter =
      fp.add(
        methodSignature(
          method,
          contextType,
          errorType
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
          contextType = Some("Context1"),
          errorType = Some("Error1")
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
          s"f.effect($delegate(request))"
        case StreamType.Bidirectional   =>
          s"f.stream($delegate(request))"
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
          s"f.effect($delegate(request))"
        case StreamType.Bidirectional   =>
          s"f.stream($delegate(request))"
      }
      fp.add(
        clientMethodSignature(
          method,
          contextType = "Any"
        ) + " = " + newImpl
      )
    }

    def printGTransformMethod(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"def transform[Context1, Error1](f: $GTransform[Context, Error, Context1, Error1]): ${gtraitName.fullName}[Context1, Error1] = new ${gtraitName.fullName}[Context1, Error1] {"
      ).indented(
        _.print(service.getMethods().asScala.toVector)(
          printServerTransform
        )
      ).add("}")

    def print(fp: FunctionalPrinter): FunctionalPrinter =
      fp.add(
        s"trait ${gtraitName.name}[-Context, +Error] extends scalapb.zio_grpc.GenericGeneratedService[Context, Error, ${gtraitName.name}] {"
      ).indented(
        _.add("self =>")
          .print(service.getMethods().asScala.toVector)(
            printMethodSignature(
              contextType = Some("Context"),
              errorType = Some("Error")
            )
          )
          .add("")
          .call(printGTransformMethod)
      ).add("}")
        .add("")
        .add(
          s"type Z${traitName.name}[Context] = ${gtraitName.name}[Context, $StatusException]",
          s"type RC${traitName.name} = ${gtraitName.name}[$RequestContext, $StatusException]"
        )
        .add("")
        .add(
          s"trait ${traitName.name} extends scalapb.zio_grpc.GeneratedService {"
        )
        .indented(
          _.add("self =>")
            .add(s"type Generic[-C, +E] = ${gtraitName.name}[C, E]")
            .print(service.getMethods().asScala.toVector)(
              printMethodSignature(contextType = None, errorType = Some(StatusException))
            )
            .add("")
            .add(
              s"override def asGeneric: ${gtraitName.name}[Any, $StatusException] = new ${gtraitName.name}[Any, $StatusException] {"
            )
            .indented(
              _.print(service.getMethods().asScala.toVector) { (fp, method) =>
                (
                  fp.add(
                    s"${methodSignature(method, contextType = Some("Any"), errorType = Some(StatusException))} = self.${method.name}(request)"
                  )
                )
              }
            )
            .add("}")
            .add(s"def transform(z: $Transform): ${gtraitName.name}[Any, $StatusException] = asGeneric.transform(z)")
            .add(
              s"def transform[C, E](z: $GTransform[Any, $StatusException, C, E]): ${gtraitName.name}[C, E] = asGeneric.transform(z)"
            )
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
                s"""def bind(serviceImpl: ${traitName.fullName}): zio.UIO[$serverServiceDef] = ${gtraitName.fullName}.genericBindable.bind(serviceImpl.asGeneric)"""
              )
            )
            .add("}")
        )
        .add("}")
        .add("")
        .add(s"object ${gtraitName.name} {")
        .indented(
          _.add(
            s"implicit val genericBindable: scalapb.zio_grpc.GenericBindable[${gtraitName.fullName}[$RequestContext, $StatusException]] = new scalapb.zio_grpc.GenericBindable[${gtraitName.fullName}[$RequestContext, $StatusException]] {"
          )
            .indented(
              _.add(
                s"""def bind(serviceImpl: ${gtraitName.fullName}[$RequestContext, $StatusException]): zio.UIO[$serverServiceDef] ="""
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
          s"class ${accessorsWithResponseMetadataClassName.name}(transforms: $ZTransform[$CallContext, $CallContext] = $GTransform.identity) extends scalapb.zio_grpc.ClientMethods[${accessorsWithResponseMetadataClassName.name}] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(printAccessorWithResponseMetadata)
            .add(
              s"override def transform(t: $ZTransform[$CallContext, $CallContext]): ${accessorsWithResponseMetadataClassName.name} = new ${accessorsWithResponseMetadataClassName.name}(transforms.andThen(t))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"trait ${clientWithResponseMetadataServiceName.name} extends scalapb.zio_grpc.ClientMethods[${clientWithResponseMetadataServiceName.name}] {"
        )
        .indented(
          _.add("self =>")
            .print(service.getMethods().asScala.toVector)(
              printClientWithResponseMetadataMethodSignature
            )
        )
        .add("}")
        .add("")
        .add(
          s"object ${clientWithResponseMetadataServiceName.name} extends ${accessorsWithResponseMetadataClassName.name} {"
        )
        .indent
        .add(
          s"private[this] class ServiceStub(channel: $ZChannel, transforms: $ZTransform[$CallContext, $CallContext])"
        )
        .add(s"    extends ${clientWithResponseMetadataServiceName.name} {")
        .indented(
          _.print(service.getMethods().asScala.toVector)(
            printClientWithResponseMetadataImpl
          )
            .add(
              s"override def transform(t: $ZTransform[$CallContext, $CallContext]): ${clientWithResponseMetadataServiceName.name} = new ServiceStub(channel, transforms.andThen(t))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"def scoped(managedChannel: $ZManagedChannel, transforms: $ZTransform[$CallContext, $CallContext] = $GTransform.identity): zio.ZIO[zio.Scope, Throwable, ${clientWithResponseMetadataServiceName.name}] = managedChannel.map {"
        )
        .add("  channel => new ServiceStub(channel, transforms)")
        .add("}")
        .add("")
        .add(
          s"def live[Context](managedChannel: $ZManagedChannel, transforms: $ZTransform[$CallContext, $CallContext] = $GTransform.identity): zio.ZLayer[Any, Throwable, ${clientWithResponseMetadataServiceName.name}] = zio.ZLayer.scoped(scoped(managedChannel, transforms))"
        )
        .outdent
        .add("}")

    def genClient(fp: FunctionalPrinter): FunctionalPrinter =
      fp
        .add("")
        .add("// accessor methods")
        .add(
          s"class ${accessorsClassName.name}(transforms: $ZTransform[$CallContext, $CallContext] = $GTransform.identity) extends scalapb.zio_grpc.ClientMethods[${accessorsClassName.name}] {"
        )
        .indented(
          _.print(service.getMethods().asScala.toVector)(printAccessor)
            .add(
              s"override def transform(t: $ZTransform[$CallContext, $CallContext]): ${accessorsClassName.name} = new ${accessorsClassName.name}(transforms.andThen(t))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"trait ${clientServiceName.name} extends scalapb.zio_grpc.ClientMethods[${clientServiceName.name}] with scalapb.zio_grpc.TransformableClient[${clientServiceName.name}] {"
        )
        .indented(
          _.add("self =>")
            .print(service.getMethods().asScala.toVector)(
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
              s"override def transform(t: $ZTransform[$CallContext, $CallContext]): ${clientServiceName.name} = new ServiceStub(underlying.transform(t))"
            )
        )
        .add("}")
        .add("")
        .add(
          s"def scoped(managedChannel: $ZManagedChannel, transforms: $ZTransform[$CallContext, $CallContext] = $GTransform.identity): zio.ZIO[zio.Scope, Throwable, ${clientServiceName.name}] = ${clientWithResponseMetadataServiceName.name}.scoped(managedChannel, transforms).map(client => new ServiceStub(client))"
        )
        .add("")
        .add(
          s"def live(managedChannel: $ZManagedChannel, transforms: $ZTransform[$CallContext, $CallContext] = $GTransform.identity): zio.ZLayer[Any, Throwable, ${clientServiceName.name}] = ${clientWithResponseMetadataServiceName.name}.live(managedChannel, transforms).map(clientEnv => zio.ZEnvironment(new ServiceStub(clientEnv.get)))"
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
      val innerCall         = s"_.transform(transforms).${method.name}(request)"
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
      val innerCall         = s"_.transform(transforms).${method.name}(request)"
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
      val Req = method.inputType.scalaType
      val Res = method.outputType.scalaType

      val clientCall      = method.streamType match {
        case StreamType.Unary           => s"$ClientCalls.withMetadata.unaryCall[$Req, $Res]"
        case StreamType.ClientStreaming => s"$ClientCalls.withMetadata.clientStreamingCall[$Req, $Res]"
        case StreamType.ServerStreaming => s"$ClientCalls.withMetadata.serverStreamingCall[$Req, $Res]"
        case StreamType.Bidirectional   => s"$ClientCalls.withMetadata.bidiCall[$Req, $Res]"
      }
      val transformMethod = method.streamType match {
        case StreamType.Unary | StreamType.ClientStreaming         => "effect"
        case StreamType.ServerStreaming | StreamType.Bidirectional => "stream"
      }
      val metadata        = method.streamType match {
        case StreamType.Unary | StreamType.ClientStreaming         =>
          s"context.metadata.flatMap"
        case StreamType.ServerStreaming | StreamType.Bidirectional =>
          s"zio.stream.ZStream.fromZIO(context.metadata).flatMap"
      }
      fp.add(
        clientWithResponseMetadataSignature(
          method,
          envOutType = "Any"
        ) + s" ="
      ).indent
        .add(s"transforms.$transformMethod { context => ")
        .indented(
          _.add(s"$metadata { headers =>")
            .indented(
              _.add(
                s"$clientCall("
              )
                .indented(
                  _.add(s"channel,")
                    .add(s"context.method.asInstanceOf[$methodDescriptor[$Req, $Res]],")
                    .add("context.options,")
                    .add("headers,")
                    .add("request")
                )
                .add(")")
            )
            .add("}")
        )
        .add(s"}($CallContext(${method.grpcDescriptor.fullName}, $CallOptions.DEFAULT, $SafeMetadata.make))")
        .outdent
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

  def stream(res: String, errType: String, envType: String) =
    envType match {
      case "Any" => s"_root_.zio.stream.Stream[$errType, $res]"
      case r     => s"_root_.zio.stream.ZStream[$r, $errType, $res]"
    }

  def io(res: String, errType: String, envType: String) =
    envType match {
      case "Any" => s"_root_.zio.IO[$errType, $res]"
      case r     => s"_root_.zio.ZIO[$r, $errType, $res]"
    }
}
