package geotrellis.elevation

import geotrellis.raster.histogram._
import geotrellis.raster.io._
import geotrellis.raster.Tile
import geotrellis.spark._
import geotrellis.spark.etl.config.EtlConf
import geotrellis.spark.etl.{Etl, OutputPlugin}
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.util.SparkUtils
import geotrellis.vector.ProjectedExtent

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkContext, SparkConf}

import scala.collection.mutable

object Ingest extends LazyLogging {

  /**
    * This is a simple version of an ingest call.  We could use this
    * if it weren't for the fact that the elevation tiles are big -
    * because we need to split them up, we need to use some finer
    * grained components of the ETL.  This should change prior to 1.0,
    * where a splitting mechanism will be available via configuration.
    */
  def simpleIngest(args: Array[String])(implicit sc: SparkContext): Unit =
    Etl.ingest[ProjectedExtent, SpatialKey, Tile](args)

  /**
    * Perform the ingest according to the configuration files.
    */
  def ingest(args: Array[String])(implicit sc: SparkContext): Unit = {
    logger.info("Ingesting data")

    EtlConf(args).foreach { conf =>
      val etl = Etl(conf)

      /** load source tiles using input module specified */
      val sourceTiles: RDD[(ProjectedExtent, Tile)] =
        etl.load[ProjectedExtent, Tile]

      /** perform the reprojection and mosaicing step to fit tiles to LayoutScheme specified */
      val (zoom, tiled) =
        etl.tile[ProjectedExtent, Tile, SpatialKey](sourceTiles)

      /** save and optionally pyramid the mosaiced layer */

      // This keeps track of what layer names we have already seen, so we don't save multiple histograms
      val s = scala.collection.mutable.Set[String]()

      etl.save[SpatialKey, Tile](LayerId(etl.input.name, zoom), tiled, { (attributeStore, layerWriter, layerId, rdd) =>
        layerWriter.write(layerId, rdd)

        // Save off histogram of the base layer, store in zoom 0's attributes.
        if(!s.contains(layerId.name)) {
          val histogram = rdd.histogram(512)
          attributeStore.write(
            layerId.copy(zoom = 0),
            "histogram",
            histogram: Histogram[Double]
          )
          s += layerId.name
        }
      })
    }

    logger.info("Data ingested")
  }

  def main(args: Array[String]): Unit = {
    implicit val sc = SparkUtils.createSparkContext("GeoTrellis ETL", new SparkConf(true))
    try {
      ingest(args)
    } finally {
      sc.stop()
    }
  }
}
