package gobblin.compaction.hivebasedconstructs;

import java.io.IOException;
import java.util.List;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.thrift.TException;
import com.google.common.base.Splitter;
import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.WorkUnitState;
import gobblin.data.management.conversion.hive.avro.AvroSchemaManager;
import gobblin.data.management.conversion.hive.watermarker.PartitionLevelWatermarker;
import gobblin.source.extractor.Extractor;
import gobblin.util.AutoReturnableObject;
import gobblin.data.management.conversion.hive.extractor.HiveBaseExtractor;
import lombok.extern.slf4j.Slf4j;


/**
 * {@link Extractor} that extracts primary key field name, delta field name, and location from hive metastore and
 * creates an {@link MRCompactionEntity}
 */
@Slf4j
public class HiveMetadataForCompactionExtractor extends HiveBaseExtractor<Void, MRCompactionEntity> {

  public static final String COMPACTION_PRIMARY_KEY = "hive.metastore.primaryKey";
  public static final String COMPACTION_DELTA = "hive.metastore.delta";

  private MRCompactionEntity compactionEntity;
  private boolean extracted = false;

  public HiveMetadataForCompactionExtractor(WorkUnitState state, FileSystem fs) throws IOException, TException, HiveException {
    super(state);

    if (Boolean.valueOf(state.getPropAsBoolean(PartitionLevelWatermarker.IS_WATERMARK_WORKUNIT_KEY))) {
      log.info("Ignoring Watermark workunit for {}", state.getProp(ConfigurationKeys.DATASET_URN_KEY));
      return;
    }

    try (AutoReturnableObject<IMetaStoreClient> client = this.pool.getClient()) {
      Table table = client.get().getTable(this.dbName, this.tableName);

      String primaryKeyString = table.getParameters().get(state.getProp(COMPACTION_PRIMARY_KEY));
      List<String> primaryKeyList = Splitter.on(',').omitEmptyStrings().trimResults().splitToList(primaryKeyString);

      String deltaString = table.getParameters().get(state.getProp(COMPACTION_DELTA));
      List<String> deltaList = Splitter.on(',').omitEmptyStrings().trimResults().splitToList(deltaString);

      String topicName = AvroSchemaManager.getSchemaFromUrl(this.hiveWorkUnit.getTableSchemaUrl(), fs).getName();
      Path dataFilesPath = new Path(table.getSd().getLocation(), topicName);

      compactionEntity = new MRCompactionEntity(primaryKeyList, deltaList, dataFilesPath, state.getProperties());
    }
  }

  @Override
  public MRCompactionEntity readRecord(MRCompactionEntity reuse) {
    if (!extracted) {
      extracted = true;
      return compactionEntity;
    } else {
      return null;
    }
  }

  @Override
  public Void getSchema() throws IOException {
    return null;
  }
}
