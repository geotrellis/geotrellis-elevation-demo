package geotrellis.elevation

import geotrellis.raster.Tile
import geotrellis.spark._
import geotrellis.spark.etl.Etl
import geotrellis.spark.etl.config.EtlConf
import geotrellis.spark.util.SparkUtils
import geotrellis.vector.ProjectedExtent

import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.rdd.RDD

object Ingest {

  // Number of partitions to split the source data into,
  // if we get OutOfMemoryErrors this needs to increase.
  val SPLIT_PARTITIONS = 1000

  /** This is a simple version of an ingest call.
    * We could use this if it weren't for the fact
    * that the elevation tiles are big - because
    * we need to split them up, we need to use
    * some finer grained components of the ETL.
    * This should change prior to 1.0, where
    * a splitting mechanism will be available via configuration.
    */
  def simpleIngest(args: Array[String])(implicit sc: SparkContext): Unit =
    Etl.ingest[ProjectedExtent, SpatialKey, Tile](args)

  def ingest(args: Array[String])(implicit sc: SparkContext): Unit =
    EtlConf(args).foreach { conf =>
      val etl = Etl(conf)

      /* load source tiles using input module specified */
      val sourceTiles: RDD[(ProjectedExtent, Tile)] = etl.load[ProjectedExtent, Tile]

      val splitTiles: RDD[(ProjectedExtent, Tile)] =
        sourceTiles
          .split(1024, 1024)
          .repartition(SPLIT_PARTITIONS)

      /* perform the reprojection and mosaicing step to fit tiles to LayoutScheme specified */
      val (zoom, tiled) = etl.tile[ProjectedExtent, Tile, SpatialKey](splitTiles)

      /* save and optionally pyramid the mosaiced layer */
      etl.save[SpatialKey, Tile](LayerId(etl.input.name, zoom), tiled)
    }

  def main(args: Array[String]): Unit = {
    implicit val sc = SparkUtils.createSparkContext("GeoTrellis ETL", new SparkConf(true))
    try {
      ingest(args)
      // val rdd =sc.parallelize(0 to 100000)
      // println(s"SUM ${rdd.sum}")
    } finally {
      sc.stop()
    }
  }
}
