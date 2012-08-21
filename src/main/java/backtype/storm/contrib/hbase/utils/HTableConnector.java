package backtype.storm.contrib.hbase.utils;

import java.io.IOException;
import java.io.Serializable;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

/**
 * HTable connector for Storm {@link Bolt}
 * <p>
 * The HBase configuration is picked up from the first <tt>hbase-site.xml</tt>
 * encountered in the classpath
 */
@SuppressWarnings("serial")
public class HTableConnector implements Serializable {
  private static final Logger LOG = Logger.getLogger(HTableConnector.class);

  private Configuration conf;
  protected HTable table;
  private String tableName;

  /**
   * Initialize HTable connection
   * 
   * @param conf
   *          The {@link TupleTableConfig}
   * @throws IOException
   */
  public HTableConnector(final TupleTableConfig conf) throws IOException {
    this.tableName = conf.getTableName();
    this.conf = HBaseConfiguration.create();

    LOG.info(String.format("Initializing connection to HBase table %s at %s",
        tableName, this.conf.get("hbase.rootdir")));

    try {
      this.table = new HTable(this.conf, this.tableName);
    } catch (IOException ex) {
      throw new IOException("Unable to establish connection to HBase table "
          + this.tableName, ex);
    }

    if (conf.isBatch()) {
      // Enable client-side write buffer
      this.table.setAutoFlush(false, true);
      LOG.info("Enabled client-side write buffer");
    }

    // If set, override write buffer size
    if (conf.getWriteBufferSize() > 0) {
      try {
        this.table.setWriteBufferSize(conf.getWriteBufferSize());

        LOG.info("Setting client-side write buffer to "
            + conf.getWriteBufferSize());
      } catch (IOException ex) {
        LOG.error(
            "Unable to set client-side write buffer size for HBase table "
                + this.tableName, ex);
      }
    }

    // Check the configured column families exist
    for (String cf : conf.getColumnFamilies()) {
      if (!columnFamilyExists(cf)) {
        throw new RuntimeException(String.format(
            "HBase table '%s' does not have column family '%s'",
            conf.getTableName(), cf));
      }
    }
  }

  /**
   * Checks to see if table contains the given column family
   * 
   * @param columnFamily
   *          The column family name
   * @return boolean
   * @throws IOException
   */
  private boolean columnFamilyExists(final String columnFamily)
      throws IOException {
    return this.table.getTableDescriptor().hasFamily(
        Bytes.toBytes(columnFamily));
  }

  /**
   * Put some data in the table
   * <p>
   * If batch mode is enabled, the update will be buffered until the buffer is
   * full or {@link #flush()} is called
   * 
   * @param put
   * @throws IOException
   */
  public void put(final Put put) throws IOException {
    this.table.put(put);
  }

  /**
   * Increments one or more columns within a single row
   * 
   * @param increment
   * @throws IOException
   */
  public void increment(final Increment increment) throws IOException {
    this.table.increment(increment);
  }

  /**
   * Explicitly flush the write buffer
   * <p>
   * Puts are automatically flushed when not in batch mode
   */
  public void flush() {
    try {
      this.table.flushCommits();
    } catch (IOException ex) {
      LOG.error("Unable to flush write buffer on HBase table " + tableName, ex);
    }
  }

  /**
   * Close the table
   */
  public void close() {
    try {
      this.table.close();
    } catch (IOException ex) {
      LOG.error("Unable to close connection to HBase table " + tableName, ex);
    }
  }
}
