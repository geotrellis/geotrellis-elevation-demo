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


object Ingest extends LazyLogging {

  /**
    * Number of partitions to split the source data into, if we get
    * OutOfMemoryErrors this needs to increase.
    */
  val SPLIT_PARTITIONS = 1000

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
    * Precompute and store layer histograms.
    */
  def precompute(args: Array[String])(implicit sc: SparkContext): Unit = {
    val conf = EtlConf(args).head
    val output = conf.output
    val outputProfile = conf.outputProfile
    val backend = output.backend
    val outputPlugin =
      Etl.defaultModules.reduce(_ union _)
        .findSubclassOf[OutputPlugin[SpatialKey, Tile, TileLayerMetadata[SpatialKey]]]
        .find { _.suitableFor(output.backend.`type`.name) }
        .getOrElse(sys.error(s"Unable to find output module of type '${output.backend.`type`.name}'"))
    val attributeStore = outputPlugin.attributes(conf)
    val layerNames = attributeStore.layerIds.map(_.name).distinct
    val reader = attributeStore match {
      case as: HadoopAttributeStore => HadoopLayerReader(as)
      case _ => throw new Exception
    }

    logger.info("Precomputing histogram(s)")
    layerNames.foreach({ layerName =>
      val maxZoom = attributeStore.layerIds
        .filter(_.name == layerName)
        .map(_.zoom)
        .reduce(math.max)
      val layerId = LayerId(layerName, math.min(9, maxZoom))
      val histogram: StreamingHistogram =
        reader
          .read[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](LayerId(layerName, 8))
          .mapPartitions({ partition =>
            Iterator(partition
              .map({ case (_, tile) => StreamingHistogram.fromTile(tile, 1<<9) })
              .reduce(_ + _)) },
            preservesPartitioning = true)
          .reduce(_ + _)

      attributeStore.write(
        LayerId(layerName, 0),
        "histogram",
        histogram.asInstanceOf[Histogram[Double]])
    })
    logger.info("Histogram(s) precomputing")
  }

  /**
    * Perform the ingest according to the configuration files.
    */
  def ingest(args: Array[String])(implicit sc: SparkContext): Unit = {
    logger.info("Ingesting data")
    EtlConf(args).foreach { conf =>
      val etl = Etl(conf)

      /** load source tiles using input module specified */
      val sourceTiles: RDD[(ProjectedExtent, Tile)] = etl.load[ProjectedExtent, Tile]

      val splitTiles: RDD[(ProjectedExtent, Tile)] =
        sourceTiles
          .split(1024, 1024)
          .repartition(SPLIT_PARTITIONS)

      /** perform the reprojection and mosaicing step to fit tiles to LayoutScheme specified */
      val (zoom, tiled) = etl.tile[ProjectedExtent, Tile, SpatialKey](splitTiles)

      /** save and optionally pyramid the mosaiced layer */
      etl.save[SpatialKey, Tile](LayerId(etl.input.name, zoom), tiled)

    }
    logger.info("Data ingested")
  }

  def main(args: Array[String]): Unit = {
    implicit val sc = SparkUtils.createSparkContext("GeoTrellis ETL", new SparkConf(true))
    try {
      ingest(args)
      precompute(args)
      // val rdd =sc.parallelize(0 to 100000)
      // println(s"SUM ${rdd.sum}")
    } finally {
      sc.stop()
    }
  }
}
