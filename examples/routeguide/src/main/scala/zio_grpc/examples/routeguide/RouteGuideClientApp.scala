package zio_grpc.examples.routeguide

import io.grpc.examples.routeguide.route_guide.ZioRouteGuide.RouteGuideClient
import io.grpc.examples.routeguide.route_guide._
import io.grpc.ManagedChannelBuilder
import zio.Console._
import zio.Random._
import scalapb.zio_grpc.ZManagedChannel
import io.grpc.Channel
import zio._
import io.grpc.Status
import zio.stream.ZStream
import scala.io.Source
import zio.Duration._

object RouteGuideClientApp extends ZIOAppDefault {
  val clientLayer: Layer[Throwable, RouteGuideClient] =
    RouteGuideClient.live(
      ZManagedChannel(
        ManagedChannelBuilder.forAddress("localhost", 8980).usePlaintext()
      )
    )

  // start: getFeature
  def getFeature(
      lat: Int,
      lng: Int
  ): ZIO[RouteGuideClient with Console, Status, Unit] =
    (for {
      f <- RouteGuideClient.getFeature(Point(lat, lng))
      _ <- printLine(s"""Found feature called "${f.name}".""").orDie
    } yield ()).catchSome {
      case status if status == Status.NOT_FOUND =>
        printLine(s"Feature not found: ${status.toString()}").orDie
    }
  // end: getFeature

  val features = RouteGuideServer.featuresDatabase.feature

  /**
    * Sends numPoints randomly chosen points from [[features]] with a variable delay in between.
    * Prints the statistics when they are sent from the server.
    */
  // start: recordRoute
  def recordRoute(numPoints: Int) =
    for {
      summary <- RouteGuideClient.recordRoute(
        ZStream
          .repeatZIO(
            nextIntBetween(0, features.size).map(features(_).getLocation)
          )
          .tap(p =>
            printLine(s"Visiting (${p.latitude}, ${p.longitude})").orDie
          )
          .schedule(Schedule.spaced(300.millis))
          .take(numPoints)
      )
      _ <- printLine(
        s"Finished trip with ${summary.pointCount} points. " +
          s"Passed ${summary.featureCount} features. " +
          s"Travelled ${summary.distance} meters. " +
          s"It took ${summary.elapsedTime} seconds."
      )
    } yield ()
  // end: recordRoute

  // start: routeChat
  val routeChat =
    for {
      res <-
        RouteGuideClient
          .routeChat(
            ZStream(
              RouteNote(
                location = Some(Point(0, 0)),
                message = "First message"
              ),
              RouteNote(
                location = Some(Point(0, 10_000_000)),
                message = "Second Message"
              ),
              RouteNote(
                location = Some(Point(10_000_000, 0)),
                message = "Third Message"
              ),
              RouteNote(
                location = Some(Point(10_000_000, 10_000_000)),
                message = "Four Message"
              )
            ).tap { note =>
              printLine(
                s"""Sending message "${note.message}" at ${note.getLocation.latitude}, ${note.getLocation.longitude}"""
              ).orDie
            }
          )
          .foreach { note =>
            printLine(
              s"""Got message "${note.message}" at ${note.getLocation.latitude}, ${note.getLocation.longitude}"""
            )
          }
    } yield ()
  // end: routeChat

  // start: appLogic
  val myAppLogic =
    for {
      // Looking for a valid feature
      _ <- getFeature(409146138, -746188906)
      // Looking for a missing feature
      _ <- getFeature(0, 0)

      // Calls listFeatures with a rectangle of interest. Prints
      // each response feature as it arrives.
      // start: listFeatures
      _ <-
        RouteGuideClient
          .listFeatures(
            Rectangle(
              lo = Some(Point(400000000, -750000000)),
              hi = Some(Point(420000000, -730000000))
            )
          )
          .zipWithIndex
          .foreach {
            case (feature, index) =>
              printLine(s"Result #${index + 1}: $feature")
          }
      // end: listFeatures

      _ <- recordRoute(10)

      _ <- routeChat
    } yield ()

  final def run =
    myAppLogic.provideCustomLayer(clientLayer).exitCode
  // end: appLogic
}
