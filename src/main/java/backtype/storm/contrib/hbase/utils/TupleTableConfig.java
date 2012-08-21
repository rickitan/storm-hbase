package backtype.storm.contrib.hbase.utils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

import backtype.storm.tuple.Tuple;

/**
 * Configuration for Storm {@link Tuple} to HBase serialization.
 */
@SuppressWarnings("serial")
public class TupleTableConfig implements Serializable {
  private String tableName;
  private String tupleRowKeyField;
  private String tupleTimestampField;
  private Map<String, Set<String>> columnFamilies;
  private boolean batch = true;
  private boolean writeToWAL = true;
  private long writeBufferSize = 0L;

  /**
   * Initialize configuration
   * 
   * @param table
   *          The HBase table name
   * @param rowKeyField
   *          The {@link Tuple} field used to set the rowKey
   */
  public TupleTableConfig(final String table, final String rowKeyField) {
    this.tableName = table;
    this.tupleRowKeyField = rowKeyField;
    this.tupleTimestampField = "";
    this.columnFamilies = new HashMap<String, Set<String>>();
  }

  /**
   * Initialize configuration
   * 
   * @param table
   *          The HBase table name
   * @param rowKeyField
   *          The {@link Tuple} field used to set the rowKey
   * @param timestampField
   *          The {@link Tuple} field used to set the timestamp
   */
  public TupleTableConfig(final String table, final String rowKeyField,
      final String timestampField) {
    this.tableName = table;
    this.tupleRowKeyField = rowKeyField;
    this.tupleTimestampField = timestampField;
    this.columnFamilies = new HashMap<String, Set<String>>();
  }

  /**
   * Add column family and column qualifier to be extracted from tuple
   * 
   * @param columnFamily
   *          The column family name
   * @param columnQualifier
   *          The column qualifier name
   */
  public void addColumn(final String columnFamily, final String columnQualifier) {
    Set<String> columns = this.columnFamilies.get(columnFamily);

    if (columns == null) {
      columns = new HashSet<String>();
    }
    columns.add(columnQualifier);

    this.columnFamilies.put(columnFamily, columns);
  }

  /**
   * Creates a HBase {@link Put} from a Storm {@link Tuple}
   * 
   * @param tuple
   *          The {@link Tuple}
   * @return {@link Put}
   */
  public Put getPutFromTuple(final Tuple tuple) {
    byte[] rowKey = Bytes.toBytes(tuple.getStringByField(tupleRowKeyField));

    long ts = 0;
    if (!tupleTimestampField.equals("")) {
      ts = tuple.getLongByField(tupleTimestampField);
    }

    Put p = new Put(rowKey);
    p.setWriteToWAL(writeToWAL);

    if (columnFamilies.size() > 0) {
      for (String cf : columnFamilies.keySet()) {
        byte[] cfBytes = Bytes.toBytes(cf);
        for (String cq : columnFamilies.get(cf)) {
          byte[] cqBytes = Bytes.toBytes(cq);
          byte[] val = Bytes.toBytes(tuple.getStringByField(cq));

          if (ts > 0) {
            p.add(cfBytes, cqBytes, ts, val);
          } else {
            p.add(cfBytes, cqBytes, val);
          }
        }
      }
    }

    return p;
  }

  /**
   * Creates a HBase {@link Increment} from a Storm {@link Tuple}
   * 
   * @param tuple
   *          The {@link Tuple}
   * @param increment
   *          The amount to increment the counter by
   * @return {@link Increment}
   */
  public Increment getIncrementFromTuple(final Tuple tuple, final long increment) {
    byte[] rowKey = Bytes.toBytes(tuple.getStringByField(tupleRowKeyField));

    Increment inc = new Increment(rowKey);
    inc.setWriteToWAL(writeToWAL);

    if (columnFamilies.size() > 0) {
      for (String cf : columnFamilies.keySet()) {
        byte[] cfBytes = Bytes.toBytes(cf);
        for (String cq : columnFamilies.get(cf)) {
          byte[] val;
          try {
            val = Bytes.toBytes(tuple.getStringByField(cq));
          } catch (IllegalArgumentException ex) {
            // if cq isn't a tuple field, use cq for counter instead of tuple
            // value
            val = Bytes.toBytes(cq);
          }
          inc.addColumn(cfBytes, val, increment);
        }
      }
    }

    return inc;
  }

  /**
   * @return the tableName
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * @return Whether batch mode is enabled
   */
  public boolean isBatch() {
    return batch;
  }

  /**
   * @param batch
   *          Whether to enable HBase's client-side write buffer. When enabled
   *          your bolt will store put operations locally until the write buffer
   *          is full, so they can be sent to HBase in a single RPC call. When
   *          disabled each put operation is effectively an RPC and is sent
   *          straight to HBase. As your bolt can process thousands of values
   *          per second it is recommended that the write buffer is enabled.
   *          <p>
   *          Enabled by default
   */
  public void setBatch(boolean batch) {
    this.batch = batch;
  }

  /**
   * @param writeToWAL
   *          Sets whether to write to HBase's edit log. Setting to false will
   *          mean fewer operations to perform when writing to HBase and hence
   *          better performance, but changes that haven't been flushed to a
   *          store file will be lost in the event of HBase failure
   *          <p>
   *          Enabled by default
   */
  public void setWriteToWAL(boolean writeToWAL) {
    this.writeToWAL = writeToWAL;
  }

  /**
   * @param writeBufferSize
   *          Overrides the client-side write buffer size.
   *          <p>
   *          By default the write buffer size is 2 MB (2097152 bytes). If you
   *          are storing larger data, you may want to consider increasing this
   *          value to allow your bolt to efficiently group together a larger
   *          number of records per RPC
   *          <p>
   *          Overrides the write buffer size you have set in your
   *          hbase-site.xml e.g. <code>hbase.client.write.buffer</code>
   */
  public void setWriteBufferSize(long writeBufferSize) {
    this.writeBufferSize = writeBufferSize;
  }

  /**
   * @return the writeBufferSize
   */
  public long getWriteBufferSize() {
    return writeBufferSize;
  }

  /**
   * @return A Set of configured column families
   */
  public Set<String> getColumnFamilies() {
    return this.columnFamilies.keySet();
  }
}
