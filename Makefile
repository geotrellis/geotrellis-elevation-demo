get-data:
	cd data && python download-and-preprocess.py lancaster-pa-1-meter.csv --download_dir download_data -o source

# Imports preprocessed elevation data GeoTiffs into HDFS
import:
	docker-compose run hdfs-name hdfs dfs -copyFromLocal /data/source/ /source

# Runs the GeoTrellis ingest into HDFS
ingest:
	sbt "project ingest" assembly
	docker-compose run spark-master spark-submit \
	  --master local[*] \
          --class geotrellis.elevation.Ingest --driver-memory 10G \
	  /ingest/target/scala-2.11/ingest-assembly-0.1.0.jar \
          --input "file:///ingest/conf/input.json" \
          --output "file:///ingest/conf/output.json" \
          --backend-profiles "file:///ingest/conf/backend-profiles.json"

ingest-local:
	sbt "project ingest" assembly
	spark-submit \
	  --master local[*] \
          --class geotrellis.elevation.Ingest --driver-memory 10G \
	  ${PWD}/ingest/target/scala-2.11/ingest-assembly-0.1.0.jar \
          --input "file://${PWD}/ingest/conf/input-file.json" \
          --output "file://${PWD}/ingest/conf/output-file.json" \
          --backend-profiles "file://${PWD}/ingest/conf/backend-profiles.json"

server-local:
	sbt "project server" assembly
	spark-submit \
	  --master local[*] --driver-memory 10G \
          --class geotrellis.elevation.Server \
	  ${PWD}/server/target/scala-2.11/server-assembly-0.1.0.jar

etl: get-data import ingest

.PHONY: get-data import ingest etl
