package io.grpc

/**
  * Dummy io.grpc.Context definition for interop with ZServerCallHandler
  */
class Context

object Context {
  val ROOT: Context      = new Context()
  def current(): Context = ROOT
}
