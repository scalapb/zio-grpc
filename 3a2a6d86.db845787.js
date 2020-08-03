(window.webpackJsonp=window.webpackJsonp||[]).push([[10],{64:function(e,t,n){"use strict";n.r(t),n.d(t,"frontMatter",(function(){return i})),n.d(t,"metadata",(function(){return c})),n.d(t,"rightToc",(function(){return s})),n.d(t,"default",(function(){return l}));var a=n(2),r=n(6),o=(n(0),n(87)),i={title:"Context and Dependencies",sidebar_label:"Context and Dependencies",custom_edit_url:"https://github.com/scalapb/zio-grpc/edit/master/docs/context.md"},c={unversionedId:"context",id:"context",isDocsHomePage:!1,title:"Context and Dependencies",description:"When implementing a server, ZIO gRPC allows you to specify that your service",source:"@site/../zio-grpc-docs/target/mdoc/context.md",permalink:"/zio-grpc/docs/context",editUrl:"https://github.com/scalapb/zio-grpc/edit/master/docs/context.md",sidebar_label:"Context and Dependencies",sidebar:"someSidebar",previous:{title:"Generated Code Reference",permalink:"/zio-grpc/docs/generated-code"}},s=[{value:"Context transformations",id:"context-transformations",children:[{value:"Accessing metadata",id:"accessing-metadata",children:[]},{value:"Depending on a service",id:"depending-on-a-service",children:[]}]},{value:"Using a service as ZLayer",id:"using-a-service-as-zlayer",children:[]}],p={rightToc:s};function l(e){var t=e.components,n=Object(r.a)(e,["components"]);return Object(o.b)("wrapper",Object(a.a)({},p,n,{components:t,mdxType:"MDXLayout"}),Object(o.b)("p",null,"When implementing a server, ZIO gRPC allows you to specify that your service\ndepends on an environment of type ",Object(o.b)("inlineCode",{parentName:"p"},"R")," and a context of type ",Object(o.b)("inlineCode",{parentName:"p"},"Context"),"."),Object(o.b)("p",null,Object(o.b)("inlineCode",{parentName:"p"},"Context")," and ",Object(o.b)("inlineCode",{parentName:"p"},"R")," can be of any Scala type, however when they are not ",Object(o.b)("inlineCode",{parentName:"p"},"Any")," they have to be wrapped in an ",Object(o.b)("inlineCode",{parentName:"p"},"Has[]"),". This allows ZIO gRPC to combine two values (",Object(o.b)("inlineCode",{parentName:"p"},"Context with R"),") when providing the values at effect execution time."),Object(o.b)("p",null,"For example, we can define a service for which the effects depend on ",Object(o.b)("inlineCode",{parentName:"p"},"Console"),", and for each request we except to get context of type ",Object(o.b)("inlineCode",{parentName:"p"},"User"),". Note that ",Object(o.b)("inlineCode",{parentName:"p"},"Console")," is a type-alias to ",Object(o.b)("inlineCode",{parentName:"p"},"Has[Console.Service]")," so there is no need wrap it once more in an ",Object(o.b)("inlineCode",{parentName:"p"},"Has"),"."),Object(o.b)("pre",null,Object(o.b)("code",Object(a.a)({parentName:"pre"},{className:"language-scala"}),'import zio.{Has, ZIO}\nimport zio.console._\nimport scalapb.zio_grpc.RequestContext\nimport myexample.testservice.ZioTestservice.ZSimpleService\nimport myexample.testservice.{Request, Response}\nimport io.grpc.Status\n\ncase class User(name: String)\n\nobject MyService extends ZSimpleService[Console, Has[User]] {\n  def sayHello(req: Request): ZIO[Console with Has[User], Status, Response] =\n    for {\n      user <- ZIO.service[User]\n      _ <- putStrLn("I am here!")\n    } yield Response(s"Hello, ${user.name}")\n}\n')),Object(o.b)("p",null,"As you can see above, we can access both the ",Object(o.b)("inlineCode",{parentName:"p"},"User")," and the ",Object(o.b)("inlineCode",{parentName:"p"},"Console")," in our effects. If one of the methods does not need to access the dependencies or context, the returned type from the method can be cleaned up to reflect that certain things are not needed."),Object(o.b)("h2",{id:"context-transformations"},"Context transformations"),Object(o.b)("p",null,"In order to be able to bind our service to a gRPC server, we need to have the\nservice's Context type to be one of the supported types:"),Object(o.b)("ul",null,Object(o.b)("li",{parentName:"ul"},Object(o.b)("inlineCode",{parentName:"li"},"Has[scalapb.zio_grpc.RequestContext]")),Object(o.b)("li",{parentName:"ul"},Object(o.b)("inlineCode",{parentName:"li"},"Has[scalapb.zio_grpc.SafeMetadata]")),Object(o.b)("li",{parentName:"ul"},Object(o.b)("inlineCode",{parentName:"li"},"Any"))),Object(o.b)("p",null,"The service ",Object(o.b)("inlineCode",{parentName:"p"},"MyService")," as defined above expects ",Object(o.b)("inlineCode",{parentName:"p"},"Has[User]")," as a context. In order to be able to bind it, we will transform it into a service that depends on a context of type ",Object(o.b)("inlineCode",{parentName:"p"},"Has[RequestContext]"),". To do this, we need to provide the function to produce a ",Object(o.b)("inlineCode",{parentName:"p"},"User")," out of a ",Object(o.b)("inlineCode",{parentName:"p"},"RequestContext"),". This way, when a request comes in, ZIO gRPC can take the ",Object(o.b)("inlineCode",{parentName:"p"},"RequestContext")," (which is request metadata such as headers and options), and use our function to construct a ",Object(o.b)("inlineCode",{parentName:"p"},"User")," and provide it into the environment of our original service."),Object(o.b)("p",null,"In many typical cases, we may need to retrieve the user from a database, and thus we are using an effectful function ",Object(o.b)("inlineCode",{parentName:"p"},"RequestContext => IO[Status, User]")," to find the user."),Object(o.b)("p",null,"For example, we can provide a function that returns an effect that always succeeds:"),Object(o.b)("pre",null,Object(o.b)("code",Object(a.a)({parentName:"pre"},{className:"language-scala"}),'val fixedUserService =\n  MyService.transformContextM((rc: RequestContext) => ZIO.succeed(User("foo")))\n// fixedUserService: ZSimpleService[Console, Has[RequestContext]] = myexample.testservice.ZioTestservice$ZSimpleService$$anon$5$$anon$6@b59c16e\n')),Object(o.b)("p",null,"and we got our service, which still depends on an environment of type ",Object(o.b)("inlineCode",{parentName:"p"},"Console"),", however the context is now ",Object(o.b)("inlineCode",{parentName:"p"},"Has[RequestContext]")," so it can be bound to a gRPC server."),Object(o.b)("h3",{id:"accessing-metadata"},"Accessing metadata"),Object(o.b)("p",null,"Here is how we would extract a user from a metadata header:"),Object(o.b)("pre",null,Object(o.b)("code",Object(a.a)({parentName:"pre"},{className:"language-scala"}),'import zio.IO\nimport scalapb.zio_grpc.{ServiceList, ServerMain}\n\nval UserKey = io.grpc.Metadata.Key.of(\n  "user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)\n// UserKey: io.grpc.Metadata.Key[String] = Key{name=\'user-key\'}\n\ndef findUser(rc: RequestContext): IO[Status, User] =\n  rc.metadata.get(UserKey).flatMap {\n    case Some(name) => IO.succeed(User(name))\n    case _          => IO.fail(Status.UNAUTHENTICATED.withDescription("No access!"))\n  }\n\nval rcService =\n  MyService.transformContextM(findUser)\n// rcService: ZSimpleService[Console, Has[RequestContext]] = myexample.testservice.ZioTestservice$ZSimpleService$$anon$5$$anon$6@1b01ef9d\n\nobject MyServer extends ServerMain {\n  def services = ServiceList.add(rcService)\n}\n')),Object(o.b)("h3",{id:"depending-on-a-service"},"Depending on a service"),Object(o.b)("p",null,"A context transformation may introduce a dependency on another service. For example, you\nmay want to organize your code such that there is a ",Object(o.b)("inlineCode",{parentName:"p"},"UserDatabase")," service that provides\na ",Object(o.b)("inlineCode",{parentName:"p"},"fetchUser")," effect that retrieves users from a database. Here is how you can do this:"),Object(o.b)("pre",null,Object(o.b)("code",Object(a.a)({parentName:"pre"},{className:"language-scala"}),"type UserDatabase = Has[UserDatabase.Service]\nobject UserDatabase {\n  trait Service {\n    def fetchUser(name: String): IO[Status, User]\n  }\n\n  // accessor\n  def fetchUser(name: String): ZIO[UserDatabase, Status, User] =\n    ZIO.accessM[UserDatabase](_.get.fetchUser(name))\n\n  val live = zio.ZLayer.succeed(\n    new Service {\n      def fetchUser(name: String): IO[Status, User] =\n        IO.succeed(User(name))\n    })\n}\n")),Object(o.b)("p",null,"Now,\nThe context transformation effect we apply may introduce an additional environmental dependency to our service. For example:"),Object(o.b)("pre",null,Object(o.b)("code",Object(a.a)({parentName:"pre"},{className:"language-scala"}),"import zio.clock._\nimport zio.duration._\n\nval myServiceAuthWithDatabase  =\n  MyService.transformContextM {\n    (rc: RequestContext) =>\n        rc.metadata.get(UserKey)\n        .someOrFail(Status.UNAUTHENTICATED)\n        .flatMap(UserDatabase.fetchUser)\n  }\n// myServiceAuthWithDatabase: ZSimpleService[Console with UserDatabase, Has[RequestContext]] = myexample.testservice.ZioTestservice$ZSimpleService$$anon$5$$anon$6@23b3a6d0\n")),Object(o.b)("p",null,"And now our service not only depends on a ",Object(o.b)("inlineCode",{parentName:"p"},"Console"),", but also on a ",Object(o.b)("inlineCode",{parentName:"p"},"UserDatabase"),"."),Object(o.b)("h2",{id:"using-a-service-as-zlayer"},"Using a service as ZLayer"),Object(o.b)("p",null,"We can turn our service into a ZLayer:"),Object(o.b)("pre",null,Object(o.b)("code",Object(a.a)({parentName:"pre"},{className:"language-scala"}),"val myServiceLive = myServiceAuthWithDatabase.toLayer\n// myServiceLive: zio.ZLayer[Console with UserDatabase, Nothing, Has[ZSimpleService[Any, Has[RequestContext]]]] = Managed(\n//   zio.ZManaged@21634692\n// )\n")),Object(o.b)("p",null,"notice how the dependencies moved to the input side of the ",Object(o.b)("inlineCode",{parentName:"p"},"Layer")," and the resulting layer is of\ntype ",Object(o.b)("inlineCode",{parentName:"p"},"ZSimpleService[Any, Has[RequestContext]]]"),", which means no environment is expected, and it assumes\na ",Object(o.b)("inlineCode",{parentName:"p"},"Has[RequestContext]")," context. To use this layer in an app, we can wire it like so:"),Object(o.b)("pre",null,Object(o.b)("code",Object(a.a)({parentName:"pre"},{className:"language-scala"}),"import scalapb.zio_grpc.ServerLayer\n\nval serverLayer =\n    ServerLayer.fromServiceLayer(\n        io.grpc.ServerBuilder.forPort(9000)\n    )(myServiceLive)\n// serverLayer: zio.ZLayer[Console with UserDatabase, Throwable, Has[scalapb.zio_grpc.Server.Service]] = Fold(\n//   Managed(zio.ZManaged@21634692),\n//   zio.ZLayer$$Lambda$11036/1644915115@46e3cd23,\n//   scalapb.zio_grpc.ServerLayer$$$Lambda$11035/41303173@30cc1022\n// )\n\nval ourApp = (UserDatabase.live ++ Console.any) >>>\n    serverLayer\n// ourApp: zio.ZLayer[Any with Console, Throwable, Has[scalapb.zio_grpc.Server.Service]] = Fold(\n//   ZipWithPar(\n//     Managed(zio.ZManaged@6d05564e),\n//     Managed(zio.ZManaged@1c2a0937),\n//     zio.ZLayer$$Lambda$11040/30241068@44f85544\n//   ),\n//   zio.ZLayer$$Lambda$11036/1644915115@46e3cd23,\n//   <function0>\n// )\n\nobject LayeredApp extends zio.App {\n    def run(args: List[String]) = ourApp.build.useForever.exitCode\n}\n")),Object(o.b)("p",null,Object(o.b)("inlineCode",{parentName:"p"},"serverLayer")," wraps around our service layer to produce a server. Then, ",Object(o.b)("inlineCode",{parentName:"p"},"ourApp")," layer is constructed such that it takes ",Object(o.b)("inlineCode",{parentName:"p"},"UserDatabase.live")," in conjuction to a passthrough layer for ",Object(o.b)("inlineCode",{parentName:"p"},"Console")," to satisfy the two input requirements of ",Object(o.b)("inlineCode",{parentName:"p"},"serverLayer"),". The outcome, ",Object(o.b)("inlineCode",{parentName:"p"},"ourApp"),", is a ",Object(o.b)("inlineCode",{parentName:"p"},"ZLayer")," that can produce a ",Object(o.b)("inlineCode",{parentName:"p"},"Server")," from a ",Object(o.b)("inlineCode",{parentName:"p"},"Console"),". In the ",Object(o.b)("inlineCode",{parentName:"p"},"run")," method we build the layer and run it. Note that we are directly using a ",Object(o.b)("inlineCode",{parentName:"p"},"zio.App")," rather than ",Object(o.b)("inlineCode",{parentName:"p"},"ServerMain")," which does\nnot support this use case yet."))}l.isMDXComponent=!0},87:function(e,t,n){"use strict";n.d(t,"a",(function(){return b})),n.d(t,"b",(function(){return m}));var a=n(0),r=n.n(a);function o(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function i(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);t&&(a=a.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,a)}return n}function c(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?i(Object(n),!0).forEach((function(t){o(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):i(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function s(e,t){if(null==e)return{};var n,a,r=function(e,t){if(null==e)return{};var n,a,r={},o=Object.keys(e);for(a=0;a<o.length;a++)n=o[a],t.indexOf(n)>=0||(r[n]=e[n]);return r}(e,t);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);for(a=0;a<o.length;a++)n=o[a],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(r[n]=e[n])}return r}var p=r.a.createContext({}),l=function(e){var t=r.a.useContext(p),n=t;return e&&(n="function"==typeof e?e(t):c(c({},t),e)),n},b=function(e){var t=l(e.components);return r.a.createElement(p.Provider,{value:t},e.children)},d={inlineCode:"code",wrapper:function(e){var t=e.children;return r.a.createElement(r.a.Fragment,{},t)}},u=r.a.forwardRef((function(e,t){var n=e.components,a=e.mdxType,o=e.originalType,i=e.parentName,p=s(e,["components","mdxType","originalType","parentName"]),b=l(n),u=a,m=b["".concat(i,".").concat(u)]||b[u]||d[u]||o;return n?r.a.createElement(m,c(c({ref:t},p),{},{components:n})):r.a.createElement(m,c({ref:t},p))}));function m(e,t){var n=arguments,a=t&&t.mdxType;if("string"==typeof e||a){var o=n.length,i=new Array(o);i[0]=u;var c={};for(var s in t)hasOwnProperty.call(t,s)&&(c[s]=t[s]);c.originalType=e,c.mdxType="string"==typeof e?e:a,i[1]=c;for(var p=2;p<o;p++)i[p]=n[p];return r.a.createElement.apply(null,i)}return r.a.createElement.apply(null,n)}u.displayName="MDXCreateElement"}}]);