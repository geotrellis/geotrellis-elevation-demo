get-data:
	cd data && python download-and-preprocess.py lancaster-pa-1-meter.csv --download_dir download_data -o source

# Imports preprocessed elevation data GeoTiffs into HDFS
import:
	docker-compose run hdfs-name hdfs dfs -copyFromLocal /data/source/ /source

# Runs the GeoTrellis ingest into Accumulo
ingest:
	sbt "project ingest" assembly
	docker-compose run spark-master spark-submit \
	  --master local[4] \
          --class geotrellis.elevation.Ingest --driver-memory 10G \
	  /ingest/target/scala-2.11/ingest-assembly-0.1.0.jar \
          --input "file:///ingest/conf/input.json" \
          --output "file:///ingest/conf/output.json" \
          --backend-profiles "file:///ingest/conf/backend-profiles.json"

.PHONY: get-data import ingest
