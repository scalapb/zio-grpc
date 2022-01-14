package zio_grpc.examples.routeguide

import io.grpc.Status
import scalapb.zio_grpc.ServerMain
import scalapb.zio_grpc.ServiceList
import zio.{Ref, ZEnv, ZIO}
import zio.stream.ZStream
import zio.console._

import io.grpc.examples.routeguide.route_guide._
import scala.math._
import zio.IO
import scalapb.json4s.JsonFormat
import scala.io.Source
import zio.clock.Clock
import zio.clock._
import zio.UIO

class RouteGuideService(
    features: Seq[Feature],
    routeNotesRef: Ref[Map[Point, List[RouteNote]]]
) extends ZioRouteGuide.ZRouteGuide[ZEnv, Any] {

  /**
    * Gets the [[io.grpc.examples.routeguide.route_guide.Feature]] at the requested [[Point]]. If no feature at
    * that location exists, a NOT FOUND error is returned.
    *
    * @param request the requested location for the feature.
    */
  // start: getFeature
  def getFeature(request: Point): ZIO[ZEnv, Status, Feature] =
    ZIO.fromOption(findFeature(request)).mapError(_ => Status.NOT_FOUND)
  // end: getFeature

  /**
    * Streams all features contained within the given bounding {@link Rectangle}.
    *
    * @param request the bounding rectangle for the requested features.
    */
  // start: listFeatures
  def listFeatures(request: Rectangle): ZStream[ZEnv, Status, Feature] = {
    val left = request.getLo.longitude min request.getHi.longitude
    val right = request.getLo.longitude max request.getHi.longitude
    val top = request.getLo.latitude max request.getHi.latitude
    val bottom = request.getLo.latitude min request.getHi.latitude

    ZStream.fromIterable(
      features.filter { feature =>
        val lat = feature.getLocation.latitude
        val lon = feature.getLocation.longitude
        lon >= left && lon <= right && lat >= bottom && lat <= top
      }
    )
  }
  // end: listFeatures

  /**
    * Gets a stream of points, and responds with statistics about the "trip": number of points,
    * number of known features visited, total distance traveled, and total time spent.
    *
    * @param request a stream of points to process
    */
  // start: recordRoute
  def recordRoute(
      request: zio.stream.Stream[Status, Point]
  ): ZIO[Clock, Status, RouteSummary] = {
    // Zips each element with the previous element, initially accompanied by None.
    request.zipWithPrevious
      .fold(RouteSummary()) {
        case (summary, (maybePrevPoint, currentPoint)) =>
          // Compute the next status based on the current status.
          summary.copy(
            pointCount = summary.pointCount + 1,
            featureCount =
              summary.featureCount + (if (findFeature(currentPoint).isDefined) 1
                                      else 0),
            distance = summary.distance + maybePrevPoint
              .map(calcDistance(_, currentPoint))
              .getOrElse(0)
          )
      }
      .timed // returns a new effect that times the execution
      .map {
        case (duration, summary) =>
          summary.copy(elapsedTime = (duration.toMillis / 1000).toInt)
      }
  }
  // end: recordRoute

  // start: routeChat
  def routeChat(
      request: zio.stream.Stream[Status, RouteNote]
  ): ZStream[ZEnv, Status, RouteNote] =
    request.flatMap { note =>
      // By using flatMap, we can map each RouteNote we receive to a stream with
      // the existing RouteNotes for that location, and those sub-streams are going
      // to get concatenated.
      // We start from an effect that updates the map with the new RouteNote,
      // and returns the notes associated with the location just before the update.
      val updateMapEffect: UIO[List[RouteNote]] =
        routeNotesRef.modify { routeNotes =>
          val messages = routeNotes.getOrElse(note.getLocation, Nil)
          (messages, routeNotes.updated(note.getLocation, note :: messages))
        }
      // We create a stream from the effect.
      ZStream.fromIterableM(updateMapEffect)
    }
  // end: routeChat

  /** Gets the feature with the given point.
    *
    * @param location the location to check
    * @return A non-empty option if a feature is defined at that point, None otherwise.
    */
  // start: findFeature
  def findFeature(point: Point): Option[Feature] =
    features.find(f => f.getLocation == point && f.name.nonEmpty)
  // end: findFeature

  /**
    * Calculate the distance between two points using the "haversine" formula.
    * The formula is based on http://mathforum.org/library/drmath/view/51879.html.
    *
    * @param start The starting point
    * @param end The end point
    * @return The distance between the points in meters
    */
  def calcDistance(start: Point, end: Point): Int = {
    val r = 6371000 // earth radius in meters
    val CoordFactor: Double = 1e7
    val lat1 = toRadians(start.latitude) / CoordFactor
    val lat2 = toRadians(end.latitude) / CoordFactor
    val lon1 = toRadians(start.longitude) / CoordFactor
    val lon2 = toRadians(end.longitude) / CoordFactor
    val deltaLat = lat2 - lat1
    val deltaLon = lon2 - lon1

    val a = sin(deltaLat / 2) * sin(deltaLat / 2)
    +cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    (r * c).toInt
  }
}

// start: serverMain
object RouteGuideServer extends ServerMain {
  override def port: Int = 8980

  val featuresDatabase = JsonFormat.fromJsonString[FeatureDatabase](
    Source.fromResource("route_guide_db.json").mkString
  )

  val createRouteGuide = for {
    routeNotes <- Ref.make(Map.empty[Point, List[RouteNote]])
  } yield new RouteGuideService(featuresDatabase.feature, routeNotes)

  def services: ServiceList[zio.ZEnv] =
    ServiceList.addM(createRouteGuide)
}
// end: serverMain
