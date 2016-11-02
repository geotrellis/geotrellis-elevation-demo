package geotrellis.elevation

import geotrellis.spark.TileLayerMetadata

case class LayerMetadata[K](rasterMetaData: TileLayerMetadata[K], times: Array[Long])
