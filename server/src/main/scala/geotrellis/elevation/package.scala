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

package geotrellis

import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.LayerId

import com.typesafe.config.Config
import org.apache.spark.SparkContext

import scala.collection.JavaConversions._


package object elevation {

  def initBackend(config: Config)(implicit cs: SparkContext): (FilteringLayerReader[LayerId], ValueReader[LayerId], AttributeStore)  = {
    config.getString("geotrellis.backend") match {
      case "hadoop" => {
        val path = config.getString("hadoop.path")
        (HadoopLayerReader(path), HadoopValueReader(path), HadoopAttributeStore(path))
      }
      case "file" => {
        val path = config.getString("file.path")
        (FileLayerReader(path), FileValueReader(path), FileAttributeStore(path))
      }
      case s => throw new Exception(s"not supported backend: $s")
    }
  }

}
