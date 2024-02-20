"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[292],{3905:function(e,t,n){n.d(t,{Zo:function(){return l},kt:function(){return m}});var r=n(7294);function a(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function i(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);t&&(r=r.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,r)}return n}function o(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?i(Object(n),!0).forEach((function(t){a(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):i(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function s(e,t){if(null==e)return{};var n,r,a=function(e,t){if(null==e)return{};var n,r,a={},i=Object.keys(e);for(r=0;r<i.length;r++)n=i[r],t.indexOf(n)>=0||(a[n]=e[n]);return a}(e,t);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);for(r=0;r<i.length;r++)n=i[r],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(a[n]=e[n])}return a}var c=r.createContext({}),p=function(e){var t=r.useContext(c),n=t;return e&&(n="function"==typeof e?e(t):o(o({},t),e)),n},l=function(e){var t=p(e.components);return r.createElement(c.Provider,{value:t},e.children)},d={inlineCode:"code",wrapper:function(e){var t=e.children;return r.createElement(r.Fragment,{},t)}},u=r.forwardRef((function(e,t){var n=e.components,a=e.mdxType,i=e.originalType,c=e.parentName,l=s(e,["components","mdxType","originalType","parentName"]),u=p(n),m=a,v=u["".concat(c,".").concat(m)]||u[m]||d[m]||i;return n?r.createElement(v,o(o({ref:t},l),{},{components:n})):r.createElement(v,o({ref:t},l))}));function m(e,t){var n=arguments,a=t&&t.mdxType;if("string"==typeof e||a){var i=n.length,o=new Array(i);o[0]=u;var s={};for(var c in t)hasOwnProperty.call(t,c)&&(s[c]=t[c]);s.originalType=e,s.mdxType="string"==typeof e?e:a,o[1]=s;for(var p=2;p<i;p++)o[p]=n[p];return r.createElement.apply(null,o)}return r.createElement.apply(null,n)}u.displayName="MDXCreateElement"},3192:function(e,t,n){n.r(t),n.d(t,{assets:function(){return l},contentTitle:function(){return c},default:function(){return m},frontMatter:function(){return s},metadata:function(){return p},toc:function(){return d}});var r=n(3117),a=n(102),i=(n(7294),n(3905)),o=["components"],s={title:"Context and Dependencies",sidebar_label:"Context and Dependencies",custom_edit_url:"https://github.com/scalapb/zio-grpc/edit/master/docs/context.md"},c=void 0,p={unversionedId:"context",id:"context",title:"Context and Dependencies",description:"When implementing a server, ZIO gRPC allows you to specify that your service",source:"@site/../zio-grpc-docs/target/mdoc/context.md",sourceDirName:".",slug:"/context",permalink:"/zio-grpc/docs/context",draft:!1,editUrl:"https://github.com/scalapb/zio-grpc/edit/master/docs/context.md",tags:[],version:"current",frontMatter:{title:"Context and Dependencies",sidebar_label:"Context and Dependencies",custom_edit_url:"https://github.com/scalapb/zio-grpc/edit/master/docs/context.md"},sidebar:"someSidebar",previous:{title:"Generated code",permalink:"/zio-grpc/docs/generated-code"},next:{title:"Decorating services",permalink:"/zio-grpc/docs/decorating"}},l={},d=[{value:"Context transformations",id:"context-transformations",level:2},{value:"Accessing metadata",id:"accessing-metadata",level:3},{value:"Context transformations that depends on a service",id:"context-transformations-that-depends-on-a-service",level:3},{value:"Using a service as ZLayer",id:"using-a-service-as-zlayer",level:2},{value:"Implementing a service with dependencies",id:"implementing-a-service-with-dependencies",level:2}],u={toc:d};function m(e){var t=e.components,n=(0,a.Z)(e,o);return(0,i.kt)("wrapper",(0,r.Z)({},u,n,{components:t,mdxType:"MDXLayout"}),(0,i.kt)("p",null,"When implementing a server, ZIO gRPC allows you to specify that your service\nmethods depend depends on a context of type ",(0,i.kt)("inlineCode",{parentName:"p"},"Context")," which can be any Scala type."),(0,i.kt)("p",null,"For example, we can define a service with handlers that expect a context of type ",(0,i.kt)("inlineCode",{parentName:"p"},"User")," for each request:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},'import zio.ZIO\nimport zio.Console\nimport zio.Console.printLine\nimport scalapb.zio_grpc.RequestContext\nimport myexample.testservice.ZioTestservice.ZSimpleService\nimport myexample.testservice.{Request, Response}\nimport io.grpc.{Status, StatusException}\n\ncase class User(name: String)\n\nobject MyService extends ZSimpleService[User] {\n  def sayHello(req: Request, user: User): ZIO[Any, StatusException, Response] =\n    for {\n      _ <- printLine("I am here!").orDie\n    } yield Response(s"Hello, ${user.name}")\n}\n')),(0,i.kt)("h2",{id:"context-transformations"},"Context transformations"),(0,i.kt)("p",null,"In order to be able to bind our service to a gRPC server, we need to have the\nservice's Context type to be one of the supported types:"),(0,i.kt)("ul",null,(0,i.kt)("li",{parentName:"ul"},(0,i.kt)("inlineCode",{parentName:"li"},"scalapb.zio_grpc.RequestContext")),(0,i.kt)("li",{parentName:"ul"},(0,i.kt)("inlineCode",{parentName:"li"},"scalapb.zio_grpc.SafeMetadata")),(0,i.kt)("li",{parentName:"ul"},(0,i.kt)("inlineCode",{parentName:"li"},"Any"))),(0,i.kt)("p",null,"The service ",(0,i.kt)("inlineCode",{parentName:"p"},"MyService")," as defined above expects ",(0,i.kt)("inlineCode",{parentName:"p"},"User")," as a context. In order to be able to bind it, we will transform it into a service that depends on a context of type ",(0,i.kt)("inlineCode",{parentName:"p"},"RequestContext"),". To do this, we need to provide the function to produce a ",(0,i.kt)("inlineCode",{parentName:"p"},"User")," out of a ",(0,i.kt)("inlineCode",{parentName:"p"},"RequestContext"),". This way, when a request comes in, ZIO gRPC can take the ",(0,i.kt)("inlineCode",{parentName:"p"},"RequestContext")," (which is request metadata such as headers and options), and use our function to construct a ",(0,i.kt)("inlineCode",{parentName:"p"},"User")," and provide it into the environment of our original service."),(0,i.kt)("p",null,"In many typical cases, we may need to retrieve the user from a database, and thus we are using an effectful function ",(0,i.kt)("inlineCode",{parentName:"p"},"RequestContext => IO[Status, User]")," to find the user."),(0,i.kt)("p",null,"For example, we can provide a function that returns an effect that always succeeds:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},'val fixedUserService =\n  MyService.transformContextZIO((rc: RequestContext) => ZIO.succeed(User("foo")))\n// fixedUserService: myexample.testservice.ZioTestservice.GSimpleService[RequestContext, StatusException] = myexample.testservice.ZioTestservice$GSimpleService$$anon$5@711a6dd8\n')),(0,i.kt)("p",null,"and we got our service with context of type ",(0,i.kt)("inlineCode",{parentName:"p"},"RequestContext")," so it can be bound to a gRPC server."),(0,i.kt)("h3",{id:"accessing-metadata"},"Accessing metadata"),(0,i.kt)("p",null,"Here is how we would extract a user from a metadata header:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},'import zio.IO\nimport scalapb.zio_grpc.{ServiceList, ServerMain}\n\nval UserKey = io.grpc.Metadata.Key.of(\n  "user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)\n// UserKey: io.grpc.Metadata.Key[String] = Key{name=\'user-key\'}\n\ndef findUser(rc: RequestContext): IO[StatusException, User] =\n  rc.metadata.get(UserKey).flatMap {\n    case Some(name) => ZIO.succeed(User(name))\n    case _          => ZIO.fail(\n      Status.UNAUTHENTICATED.withDescription("No access!").asException)\n  }\n\nval rcService =\n  MyService.transformContextZIO(findUser)\n// rcService: myexample.testservice.ZioTestservice.GSimpleService[RequestContext, StatusException] = myexample.testservice.ZioTestservice$GSimpleService$$anon$5@3d0828ae\n\nobject MyServer extends ServerMain {\n  def services = ServiceList.add(rcService)\n}\n')),(0,i.kt)("h3",{id:"context-transformations-that-depends-on-a-service"},"Context transformations that depends on a service"),(0,i.kt)("p",null,"A context transformation may introduce a dependency on another service. For example, you\nmay want to organize your code such that there is a ",(0,i.kt)("inlineCode",{parentName:"p"},"UserDatabase")," service that provides\na ",(0,i.kt)("inlineCode",{parentName:"p"},"fetchUser")," effect that retrieves users from a database. Here is how you can do this:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},"trait UserDatabase {\n  def fetchUser(name: String): IO[StatusException, User]\n}\n\nobject UserDatabase {\n  val layer = zio.ZLayer.succeed(\n    new UserDatabase {\n      def fetchUser(name: String): IO[StatusException, User] =\n        ZIO.succeed(User(name))\n    })\n}\n")),(0,i.kt)("p",null,"Now, The context transformation effect we apply may introduce an additional environmental dependency to our service. For example:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},'import zio.Clock._\nimport zio.Duration._\n\nval myServiceAuthWithDatabase: ZIO[UserDatabase, Nothing, ZSimpleService[RequestContext]] =\n  ZIO.serviceWith[UserDatabase](\n    userDatabase =>\n      MyService.transformContextZIO {\n        (rc: RequestContext) =>\n            rc.metadata.get(UserKey)\n            .someOrFail(Status.UNAUTHENTICATED.asException)\n            .flatMap(userDatabase.fetchUser(_))\n      }\n  )\n// myServiceAuthWithDatabase: ZIO[UserDatabase, Nothing, ZSimpleService[RequestContext]] = OnSuccess(\n//   trace = "repl.MdocSession.MdocApp.myServiceAuthWithDatabase(context.md:104)",\n//   first = Sync(\n//     trace = "repl.MdocSession.MdocApp.myServiceAuthWithDatabase(context.md:104)",\n//     eval = zio.ZIO$ServiceWithZIOPartiallyApplied$$$Lambda$13269/0x0000000103826c40@1ae6c142\n//   ),\n//   successK = zio.ZIO$$$Lambda$13260/0x0000000103816040@3b428393\n// )\n')),(0,i.kt)("p",null,"Now our service can be built from an effect that depends on ",(0,i.kt)("inlineCode",{parentName:"p"},"UserDatabase"),". This effect can be\nadded to a ",(0,i.kt)("inlineCode",{parentName:"p"},"ServiceList")," using ",(0,i.kt)("inlineCode",{parentName:"p"},"addZIO"),":"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},"object MyServer2 extends ServerMain {\n  def services = ServiceList\n    .addZIO(myServiceAuthWithDatabase)\n    .provide(UserDatabase.layer)\n}\n")),(0,i.kt)("h2",{id:"using-a-service-as-zlayer"},"Using a service as ZLayer"),(0,i.kt)("p",null,"If you require more flexibility than provided through ",(0,i.kt)("inlineCode",{parentName:"p"},"ServerMain"),", you can construct\nthe server directly."),(0,i.kt)("p",null,"We first turn our service into a ZLayer:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},"val myServiceLayer = zio.ZLayer(myServiceAuthWithDatabase)\n// myServiceLayer: zio.ZLayer[UserDatabase, Nothing, ZSimpleService[RequestContext]] = Suspend(\n//   self = zio.ZLayer$$$Lambda$13271/0x0000000103825c40@29f3ea36\n// )\n")),(0,i.kt)("p",null,"Notice how the dependencies moved to the input side of the ",(0,i.kt)("inlineCode",{parentName:"p"},"ZLayer")," and the resulting layer is of\ntype ",(0,i.kt)("inlineCode",{parentName:"p"},"ZSimpleService[RequestContext]"),"."),(0,i.kt)("p",null,"To use this layer in an app, we can wire it like so:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},"import scalapb.zio_grpc.ServerLayer\nimport scalapb.zio_grpc.Server\nimport zio.ZLayer\n\nval serviceList = ServiceList\n  .addFromEnvironment[ZSimpleService[RequestContext]]\n// serviceList: ServiceList[Any with ZSimpleService[RequestContext]] = scalapb.zio_grpc.ServiceList@6eded9eb\n\nval serverLayer =\n    ServerLayer.fromServiceList(\n        io.grpc.ServerBuilder.forPort(9000),\n        serviceList\n    )\n// serverLayer: ZLayer[Any with ZSimpleService[RequestContext], Throwable, Server] = Suspend(\n//   self = zio.ZLayer$ScopedEnvironmentPartiallyApplied$$$Lambda$13282/0x0000000103830040@421ef9a3\n// )\n\nval ourApp =\n    ZLayer.make[Server](\n      serverLayer,\n      myServiceLayer,\n      UserDatabase.layer\n    )\n// ourApp: ZLayer[Any, Throwable, Server] = Suspend(\n//   self = zio.ZLayer$ZLayerProvideSomeOps$$$Lambda$13284/0x0000000103837840@6defa468\n// )\n\nobject LayeredApp extends zio.ZIOAppDefault {\n    def run = ourApp.launch.exitCode\n}\n")),(0,i.kt)("p",null,(0,i.kt)("inlineCode",{parentName:"p"},"serverLayer")," creates a ",(0,i.kt)("inlineCode",{parentName:"p"},"Server")," from a ",(0,i.kt)("inlineCode",{parentName:"p"},"ZSimpleService")," layer and still depends on a ",(0,i.kt)("inlineCode",{parentName:"p"},"UserDatabase"),". Then, ",(0,i.kt)("inlineCode",{parentName:"p"},"ourApp")," feeds a ",(0,i.kt)("inlineCode",{parentName:"p"},"UserDatabase.layer")," into ",(0,i.kt)("inlineCode",{parentName:"p"},"serverLayer")," to produce\na ",(0,i.kt)("inlineCode",{parentName:"p"},"Server")," that doesn't depend on anything. In the ",(0,i.kt)("inlineCode",{parentName:"p"},"run")," method we launch the server layer."),(0,i.kt)("h2",{id:"implementing-a-service-with-dependencies"},"Implementing a service with dependencies"),(0,i.kt)("p",null,"In this scenario, your service depends on two additional services, ",(0,i.kt)("inlineCode",{parentName:"p"},"DepA")," and ",(0,i.kt)("inlineCode",{parentName:"p"},"DepB"),".  Following ",(0,i.kt)("a",{parentName:"p",href:"https://zio.dev/reference/service-pattern/"},"ZIO's service pattern"),", we accept the (interaces of the ) dependencies as constructor parameters."),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},'trait DepA {\n  def methodA(param: String): ZIO[Any, Nothing, Int]\n}\n\nobject DepA {\n  val layer = ZLayer.succeed[DepA](new DepA {\n    def methodA(param: String) = ???\n  })\n}\n\nobject DepB {\n  val layer = ZLayer.succeed[DepB](new DepB {\n    def methodB(param: Float) = ???\n  })\n}\n\ntrait DepB {\n  def methodB(param: Float): ZIO[Any, Nothing, Double]\n}\n\ncase class MyService2(depA: DepA, depB: DepB) extends ZSimpleService[User] {\n  def sayHello(req: Request, user: User): ZIO[Any, StatusException, Response] =\n    for {\n      num1 <- depA.methodA(user.name)\n      num2 <- depB.methodB(12.3f)\n      _ <- printLine("I am here $num1 $num2!").orDie\n    } yield Response(s"Hello, ${user.name}")\n}\n\nobject MyService2 {\n  val layer: ZLayer[DepA with DepB, Nothing, ZSimpleService[RequestContext]] =\n    ZLayer.fromFunction {\n      (depA: DepA, depB: DepB) =>\n        MyService2(depA, depB).transformContextZIO(findUser(_))\n  }\n}\n')),(0,i.kt)("p",null,"Our service layer now depends on the ",(0,i.kt)("inlineCode",{parentName:"p"},"DepA")," and ",(0,i.kt)("inlineCode",{parentName:"p"},"DepB")," interfaces. A server can be created like this:"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},"object MyServer3 extends zio.ZIOAppDefault {\n\n  val serverLayer =\n    ServerLayer.fromServiceList(\n      io.grpc.ServerBuilder.forPort(9000),\n      ServiceList.addFromEnvironment[ZSimpleService[RequestContext]]\n    )\n\n  val appLayer = ZLayer.make[Server](\n    serverLayer,\n    DepA.layer,\n    DepB.layer,\n    MyService2.layer\n  )\n\n  def run = ourApp.launch.exitCode\n}\n")))}m.isMDXComponent=!0}}]);