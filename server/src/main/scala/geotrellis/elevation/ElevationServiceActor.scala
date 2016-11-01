/*
 * Copyright (c) 2016 Azavea.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.elevation

import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.io._
import geotrellis.raster.mapalgebra.local._
import geotrellis.raster.render._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.AttributeStore.Fields
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.Polygon
import geotrellis.vector.reproject._

import scala.util.Try
import scala.collection.JavaConversions._

import akka.actor._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.{SparkConf, SparkContext}


class ElevationServiceActor(override val staticPath: String, config: Config)
    extends Actor
    with ElevationService
    with LazyLogging {

  val conf = AvroRegistrator(
    new SparkConf()
      .setAppName("Elevation")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")
  )

  implicit val sparkContext = new SparkContext(conf)

  override def actorRefFactory = context
  override def receive = runRoute(serviceRoute)

  lazy val (reader, tileReader, attributeStore) = initBackend(config)

  val layerNames = attributeStore.layerIds.map(_.name).distinct

  val histogramZoomLevel = 8
  val breaksMap: Map[String, Array[Double]] =
    layerNames
      .map({ layerName =>
        val id = LayerId(layerName, 0)
        val histogram =
          Try(
            attributeStore
              .read[Histogram[Double]](id, "histogram").asInstanceOf[StreamingHistogram]
          ).getOrElse(
            reader
              .read[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](LayerId(layerName, histogramZoomLevel))
              .histogram
          )

        (layerName -> histogram.quantileBreaks(1<<8))
      })
      .toMap

  logger.info("Breaks computed")
}

trait ElevationService
    extends HttpService
    with CORSSupport {

  implicit val sparkContext: SparkContext
  implicit val executionContext = actorRefFactory.dispatcher
  val reader: FilteringLayerReader[LayerId]
  val tileReader: ValueReader[LayerId]
  val attributeStore: AttributeStore

  val staticPath: String
  val baseZoomLevel = 9

  def layerId(layer: String): LayerId =
    LayerId(layer, baseZoomLevel)

  def getMetaData(id: LayerId): TileLayerMetadata[SpatialKey] =
    attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](id)

  def serviceRoute =
    pathPrefix("gt") {
      pathPrefix("tms")(tms) ~
      pathPrefix("mean")(polygonalMean)
    } ~
  get {
    pathEndOrSingleSlash {
      getFromFile(staticPath + "/index.html")
    } ~
    pathPrefix("") {
      getFromDirectory(staticPath)
    }
  }

  def breaksMap: Map[String, Array[Double]]

  /** http://localhost:8777/gt/tms/{z}/{x}/{y}?colorRamp=blue-to-yellow-to-red-heatmap */
  def tms = pathPrefix(IntNumber / IntNumber / IntNumber) {
    (zoom, x, y) => {
      get {
        parameters('colorRamp ? "blue-to-red") {
          (colorRamp) => {
            val key = SpatialKey(x, y)

            val tile = tileReader
              .reader[SpatialKey, Tile](LayerId("elevation", zoom))
              .read(key)

            val breaks = breaksMap.getOrElse("elevation", throw new Exception)
            val ramp = ColorRampMap.getOrElse(colorRamp, ColorRamps.BlueToRed).toColorMap(breaks)

            respondWithMediaType(MediaTypes.`image/png`) {
              complete(tile.renderPng(ramp).bytes)
            }
          }
        }
      }
    }
  }

  /** http://localhost:8777/gt/mean */
  def polygonalMean = {
    import scala.concurrent.Future
    import geotrellis.vector._
    import spray.json.DefaultJsonProtocol._

    cors {
      post {
        entity(as[String]) { json =>
          complete {
            Future {
              val zoom = 18
              val layerId = LayerId("elevation", zoom)

              /** Retrieve the raw geometry that was POSTed to the endpoint */
              val rawGeometry = try {
                json.parseJson.convertTo[Geometry]
              } catch {
                case e: Exception => sys.error("THAT PROBABLY WASN'T GEOMETRY")
              }

              /** Convert the raw geometry into either a (multi|)polygon */
              val geometry = rawGeometry match {
                case p: Polygon => MultiPolygon(p.reproject(LatLng, WebMercator))
                case mp: MultiPolygon => mp.reproject(LatLng, WebMercator)
                case _ => sys.error(s"BAD GEOMETRY")
              }

              /** Compute the bounding box of the query geometry */
              val extent = geometry.envelope

              /** Fetch an RDD scoped to the bounding box of the query geometry */
              val rdd0 = reader
                .query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](layerId)
                .where(Intersects(extent))
                .result

              /** Compute the polygonal mean of the query geometry */
              val answer = rdd0.polygonalMean(geometry)

              JsObject("answer" -> JsNumber(answer))
            }
          }
        }
      }
    }
  }

}
