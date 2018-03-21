package com.hashmapinc.tempus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
/*import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SaveMode;
*/
//import com.hashmapinc.tempus.FacilityCompactionStatus;
import com.hashmapinc.tempus.TagData;
import com.hashmapinc.tempus.TagDataCompressed;
import com.hashmapinc.tempus.Utils;

public class DatabaseService {

  private transient static Logger log = Logger.getLogger(DatabaseService.class);

  private static Integer EPOCH_START_TIME = 18000;

  private static Connection dbConnection;
  //private transient PreparedStatement queryAssetDataStatusStmt;
  //private transient PreparedStatement queryDistinctTagDataStmt;
  //private transient PreparedStatement queryDistinctTagListStmt;
  //private transient PreparedStatement queryMinMaxTagDataStmt;
  //private transient PreparedStatement queryTagDataDataTypeStmt;

  //private transient PreparedStatement upsertTagDataCompressedStmt;
  //private transient PreparedStatement deleteTagDataStmt;
  private transient PreparedStatement upsertCompactionStatusStmt;
  private transient PreparedStatement upsertTduStmt;
  
  private static String tagListTable;
  private static String tagDataTable;
  private static String compactionTable;
  private static String compactionStatusTable;

  //TODO Remove all such comments starting with //*
  /////////////////////////////////////////
  private static String phoenixJdbcUrl;
  private static String hbaseZookeeperUrl;


  /**
   * @return the tagListTable
   */
  public static String getTagListTable() {
    return tagListTable;
  }

  /**
   * @param tagListTable the tagDataListTable to set
   */
  public static void setTagListTable(String tagListTable) {
    if (tagListTable == null) {
      throw new IllegalArgumentException("tagListTable");
    }
    DatabaseService.tagListTable = tagListTable;
  }

  /**
   * @return the tagDataTable
   */
  public static String getTagDataTable() {
    return tagDataTable;
  }

  /**
   * @param tagDataTable the tagDataTable to set
   */
  public static void setTagDataTable(String tagDataTable) {
    if (tagDataTable == null) {
      throw new IllegalArgumentException("tagDataTable");
    }
    DatabaseService.tagDataTable = tagDataTable.toLowerCase();
  }

  /**
   * @return the compactionTable
   */
  public static String getCompactionTable() {
    return compactionTable;
  }

  /**
   * @param compactionTable the compactionTable to set
   */
  public static void setCompactionTable(String compactionTable) {
    if (compactionTable == null) {
      throw new IllegalArgumentException("compactionTable");
    }
    DatabaseService.compactionTable = compactionTable.toLowerCase();
  }
  
  /**
   * @return the compactionStatusTable
   */
  public static String getCompactionStatusTable() {
    return compactionStatusTable;
  }

  /**
   * @param compactionStatusTable the compactionStatusTable to set
   */
  public static void setCompactionStatusTable(String compactionStatusTable) {
    if (compactionStatusTable == null) {
      throw new IllegalArgumentException("compactionStatusTable");
    }
    DatabaseService.compactionStatusTable = compactionStatusTable.toLowerCase();
  }

  /**
   * @return the phoenixJdbcUrl
   */
  public static String getPhoenixJdbcUrl() {
    return DatabaseService.phoenixJdbcUrl;
  }

  /**
   * @param jdbcUrl the phoenixJdbcUrl to set
   */
  private static void setPhoenixJdbcUrl(String jdbcUrl) {
    if (jdbcUrl == null || jdbcUrl.length() == 0) {
      throw new IllegalArgumentException("jdbcUrl");
    }
    DatabaseService.phoenixJdbcUrl =
        jdbcUrl.startsWith("jdbc:phoenix:") ? jdbcUrl : ("jdbc:phoenix:" + jdbcUrl);
  }

  /**
   * @return the hbaseZookeeperUrl
   */
  public String getHbaseZookeeperUrl() {
    return hbaseZookeeperUrl;
  }

  /**
   * @param hbaseZookeeperUrl the hbaseZookeeperUrl to set
   * @throws ConfigurationException
   */
  public void setHbaseZookeeperUrl(String hbaseZookeeperUrl) throws ConfigurationException {
    if (hbaseZookeeperUrl == null) {
      throw new IllegalArgumentException("hbaseZookeeperUrl");
    }
    Utils.validateHbaseZookeeperUrl(hbaseZookeeperUrl);
    this.hbaseZookeeperUrl = hbaseZookeeperUrl;
  }

  /**
   * @return the dbConnection
   */
  private Connection getDbConnection() {
    return dbConnection;
  }

  public static void openConnection() throws SQLException, ClassNotFoundException {
    String jdbcUrl = getPhoenixJdbcUrl();
    if (jdbcUrl == null) {
      throw new IllegalStateException("can't open connection, no jdbc url defined");
    }

    if (dbConnection != null) {
      closeConnection();
    }

    // TODO
    // we shouldnt need this yet to check
    Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
    dbConnection = DriverManager.getConnection(jdbcUrl);
    dbConnection.setAutoCommit(true);

    if (log.isDebugEnabled()) {
      log.debug("Opened connection to: " + jdbcUrl);
    }
  }

  public static void closeConnection() {
    if (dbConnection != null) {
      try {
        dbConnection.close();

        if (log.isDebugEnabled()) {
          log.debug("Closed Connection to: " + getPhoenixJdbcUrl());
        }

      } catch (Exception ex) {
        log.error("Error closing connection: ", ex);
      } /*finally {
        clearPreparedStatements();
      }*/
    }
  }

  /**
   * Test if the connection is opened
   * @return
   * @throws SQLException
   */
  public boolean hasConnection() {
    if (dbConnection != null) {
      return true;
    }
    return false;
  }

  public DatabaseService() throws ConfigurationException {
    String hbaseZookeeperUrl = System.getenv("PHOENIX_CONN_PARAM");
    if ((null == hbaseZookeeperUrl) || (0 == hbaseZookeeperUrl.length())) {
      throw new ConfigurationException(
          "Please set PHOENIX_CONN_PARAM environment variable with value as Zookeeper Quorum");
    }
    this.hbaseZookeeperUrl = hbaseZookeeperUrl;
    DatabaseService.setPhoenixJdbcUrl(hbaseZookeeperUrl);
  }
  
  /**
   * @param hbaseZookeeperUrl
   * @throws ConfigurationException
   */
  public DatabaseService(String hbaseZookeeperUrl) {
    this.hbaseZookeeperUrl = hbaseZookeeperUrl;
    DatabaseService.setPhoenixJdbcUrl(DatabaseService.hbaseZookeeperUrl);
  }

  protected void clearPreparedStatements() {
    //queryAssetDataStatusStmt = null;
    //queryDistinctTagListStmt = null;
    //queryDistinctTagDataStmt = null;
    //queryMinMaxTagDataStmt = null;
    //upsertTagDataCompressedStmt = null;
    //deleteTagDataStmt = null;
    upsertCompactionStatusStmt = null;
    upsertTduStmt = null;
  }

  public List<TagList> getDistinctURI(int numRetries, long retryAfterMillis) {
    for (int i = 0; i < numRetries; i++) {
      try {
        List<TagList> tagList = queryTagList();
        return tagList;
      } catch (Exception e) {
        log.error("Error getting asset list: ", e);
      }

      try {
        log.info("Retrying in: " + (retryAfterMillis * (i + 1)) + "ms.");
        Thread.sleep(retryAfterMillis * (i + 1));
      } catch (InterruptedException e) {
        log.error("Interrupted retrying: ", e);
        return null;
      }
    }
    return null;
  }
  
  public List<TagList> queryTagList() throws SQLException {
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }

    PreparedStatement queryDistinctTagListStmt = null;
    //if (queryDistinctTagListStmt == null) {
      queryDistinctTagListStmt = getDbConnection().prepareStatement("SELECT id, datatype from "
          + tagListTable + " where status != 0 ");
    //} else {
      //queryDistinctTagListStmt.clearParameters();
    //}

    List<TagList> uris = new ArrayList<TagList>();
    long start = System.currentTimeMillis();
    ResultSet results = queryDistinctTagListStmt.executeQuery();

    while (results.next()) {
      TagList tl = new TagList();
      tl.setId(results.getInt(1));
      tl.setDataType(results.getString(2));
      uris.add(tl);
      if (log.isTraceEnabled()) {
        log.info("TagList: " + tl.toString());
      }
    }

    if (log.isDebugEnabled()) {
      log.debug("Queried distinct URI : " + uris.size() + " records in "
          + (System.currentTimeMillis() - start) + "ms.");
    }
    return uris;
  }
  /*
  public List<Integer> getDistinctURI(int numRetries, long retryAfterMillis, long startTs,
      long endTs) {
    for (int i = 0; i < numRetries; i++) {
      try {
        List<Integer> uris = queryTagData(startTs, endTs);
        return uris;
      } catch (Exception e) {
        log.error("Error getting asset list: ", e);
      }

      try {
        log.info("Retrying in: " + (retryAfterMillis * (i + 1)) + "ms.");
        Thread.sleep(retryAfterMillis * (i + 1));
      } catch (InterruptedException e) {
        log.error("Interrupted retrying: ", e);
        return null;
      }
    }
    return null;
  }

  public List<Integer> queryTagData(long startTs, long endTs) throws SQLException {
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }

    PreparedStatement queryDistinctTagDataStmt = null;
    if (queryDistinctTagDataStmt == null) {
      queryDistinctTagDataStmt = getDbConnection().prepareStatement("SELECT DISTINCT(id) from "
          + tagDataTable + " where ts <= TO_TIMESTAMP(?) and ts >= TO_TIMESTAMP(?)");
    } else {
      queryDistinctTagDataStmt.clearParameters();
    }

    Timestamp startTime = new Timestamp(convertToUTC(startTs));
    Timestamp endTime = new Timestamp(convertToUTC(endTs));

    queryDistinctTagDataStmt.setString(1, endTime.toString());
    queryDistinctTagDataStmt.setString(2, startTime.toString());
    log.info("startTime:- " + startTime.toString() + "; endTime:- " + endTime.toString());
    List<Integer> uris = new ArrayList<Integer>();
    long start = System.currentTimeMillis();
    ResultSet results = queryDistinctTagDataStmt.executeQuery();

    while (results.next()) {
      uris.add(results.getInt(1));
      if (log.isTraceEnabled()) {
        log.trace("TagData: " + results.getString(1));
      }
    }

    if (log.isDebugEnabled()) {
      log.debug("Queried distinct URI : " + uris.size() + " records in "
          + (System.currentTimeMillis() - start) + "ms.");
    }
    return uris;
  }
*/
  public TagData getMinMaxTs(long uri) throws SQLException {
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }

    PreparedStatement queryMinMaxTagDataStmt = null;
    //if (queryMinMaxTagDataStmt == null) {
      queryMinMaxTagDataStmt = getDbConnection()
          .prepareStatement("SELECT MIN(ts), MAX(ts) FROM " + tagDataTable + " where id = ?");
    //} else {
      //queryMinMaxTagDataStmt.clearParameters();
    //}

    long start = System.currentTimeMillis();
    queryMinMaxTagDataStmt.setLong(1, uri);
    ResultSet results = queryMinMaxTagDataStmt.executeQuery();
    if (!results.next()) {
      return null;
    }

    TagData uriDetails = new TagData();
    uriDetails.setUri(uri);
    uriDetails.setMinTs(results.getTimestamp(1));
    uriDetails.setMaxTs(results.getTimestamp(2));

    if (log.isDebugEnabled()) {
      log.debug(
        "Queried min and max TS for uri: " + uri + " in " + (System.currentTimeMillis() - start) + " ms.");
    }
    return uriDetails;
  }

  public int upsertCompactedRecords(List<TagDataCompressed> tdcList, Boolean jdbcUpserts)
      throws Exception {
    if (tdcList == null) {
      throw new IllegalArgumentException("tdcList");
    }

    if (jdbcUpserts) {
      return upsertCompactedPointTagsJdbc(tdcList);
    } else {
      return 0;//*writeTagDataCompressed(sparkContext.parallelize(tdcList));
    }
  }

  public int upsertCompactedPointTagsJdbc(List<TagDataCompressed> compressedPointTags)
      throws SQLException {

    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }

    PreparedStatement upsertTagDataCompressedStmt = null;
    //if (upsertTagDataCompressedStmt != null) upsertTagDataCompressedStmt.clearParameters();

    dbConnection.setAutoCommit(false);
    int numRowsUpserted = 0;
    long start = System.currentTimeMillis();
    for (TagDataCompressed tdc : compressedPointTags) {
      if (tdc == null) {
        throw new IllegalArgumentException("tagDataCompressed");
      }
      upsertTagDataCompressedStmt = getDbConnection().prepareStatement("UPSERT INTO "
          + compactionTable + " (id, stts, vb, q, ts, ns, upts) " + " VALUES(?, ?, ?, ?, ?, ?, ?)");
      upsertTagDataCompressedStmt.setLong(1, tdc.getId());
      upsertTagDataCompressedStmt.setDate(2, new Date(tdc.getStTs().getTime()));
      upsertTagDataCompressedStmt.setBytes(3, tdc.getVb());
      upsertTagDataCompressedStmt.setBytes(4, tdc.getQ());
      upsertTagDataCompressedStmt.setBytes(5, tdc.getTs());
      upsertTagDataCompressedStmt.setLong(6, tdc.getNs());
      upsertTagDataCompressedStmt.setDate(7, new Date(System.currentTimeMillis()));

      numRowsUpserted += upsertTagDataCompressedStmt.executeUpdate();
    }
    dbConnection.commit();
    if (log.isDebugEnabled()) {
      log.info("Upserted [" + numRowsUpserted + "] Tag Data Compacted records. Completed  in "
          + (System.currentTimeMillis() - start) + "ms.");
    }
    dbConnection.setAutoCommit(true);
    return numRowsUpserted;
  }

  //*
  /*
  private void writeTagDataCompressed(JavaRDD<TagDataCompressed> tagData) throws Exception {

    if (tagData == null) {
      throw new IllegalArgumentException("tagData");
    }

    tagData.cache();
    DataFrame dataFrame = getSqlContext().createDataFrame(tagData, TagDataCompressed.class);
    long start = System.currentTimeMillis();
    dataFrame.write().format("org.apache.phoenix.spark").mode(SaveMode.Overwrite)
        .options(createSparkOptions(compactionTable)).save();
    //log.debug("DF Count " + dataFrame.count());
    if (log.isDebugEnabled()) {
      log.debug(
        "TagDataCompressed DataFrame Write in: " + (System.currentTimeMillis() - start) + "ms. ");
    }
    tagData.unpersist();
  }*/

  //*
  /*
  public void writeTagDataCompressed(List<JavaRDD<TagDataCompressed>> tagDataList)
      throws Exception {

    if (tagDataList == null) {
      throw new IllegalArgumentException("tagDataList");
    }

    log.debug("Calling DF upserts for list size  " + tagDataList.size());
    JavaRDD<TagDataCompressed> compactedRDDs = null;
    for (JavaRDD<TagDataCompressed> tdc : tagDataList) {
      if (compactedRDDs == null) {
        compactedRDDs = tdc;
      } else {
        compactedRDDs = compactedRDDs.union(tdc);
      }
    }
    writeTagDataCompressed(compactedRDDs);
  }*/

  //*
  /*
  public void upsertCompactedPointTags(List<TagDataCompressed> compressedPointTags)
      throws Exception {

    log.debug("Calling DF upserts for list size  " + compressedPointTags.size());
    JavaRDD<TagDataCompressed> compactedRDD = sparkContext.parallelize(compressedPointTags);
    writeTagDataCompressed(compactedRDD);
  }
  */

  public void upsertCompactionStatus(CompactionStatus compactionStatus)
      throws SQLException {
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }

    if (upsertCompactionStatusStmt == null) {
      upsertCompactionStatusStmt = getDbConnection().prepareStatement(
        "UPSERT into " + compactionStatusTable + " (lcts, numcomp) values (?, ?)");
    } else {
      upsertCompactionStatusStmt.clearParameters();
    }
    long start = System.currentTimeMillis();
    upsertCompactionStatusStmt.setDate(1,
      new Date(compactionStatus.getLastCompactionTs().getTime()));
    upsertCompactionStatusStmt.setLong(2, compactionStatus.getNumCompactedRecords());

    upsertCompactionStatusStmt.executeUpdate();

    if (log.isDebugEnabled()) {
      log.debug("Upsert AssetDataStatus time: " + (System.currentTimeMillis() - start) + "ms.");
    }
  }

  public int deleteCompactedURIs(List<Long> compactedURIs, long startTs, long endTs)
      throws SQLException {
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }

    if (compactedURIs == null)
      throw new IllegalArgumentException("tagDataCompressed");
    long start = System.currentTimeMillis();

    // if (deleteTagDataStmt != null) deleteTagDataStmt.clearParameters();
    int delURIsSize = compactedURIs.size();
    if(delURIsSize == 0)
      return 0;
    PreparedStatement deleteTagDataStmt = null;
    //if (deleteTagDataStmt == null) {
      StringBuilder sql = new StringBuilder();
      sql.append("DELETE FROM " + tagDataTable
          + " WHERE ts >= TO_TIMESTAMP(?) AND ts <= TO_TIMESTAMP(?) AND id in (");
      for (int i = 0; i < delURIsSize; i++) {
        sql.append("?");
        if (i + 1 < delURIsSize) {
          sql.append(",");
        }
      }
      sql.append(")");
      deleteTagDataStmt = getDbConnection().prepareStatement(sql.toString());
   // } //else {
      //deleteTagDataStmt.clearParameters();
    //}

    // convert to utc because startTs and endTs are in current time zone
    Timestamp delStartTime = new Timestamp(startTs == 0 ? convertToUTC(EPOCH_START_TIME) : convertToUTC(startTs));
    Timestamp delEndTime = new Timestamp(convertToUTC(endTs));

    if(log.isTraceEnabled()){
      log.info("delStartTime:" + delStartTime.toString());
      log.info("delEndTime:" + delEndTime.toString());
      log.info("delStartTime:" + delStartTime.getTime());
      log.info("delEndTime:" + delEndTime.getTime());
    }
    String stTime = delStartTime.toString();
    String endTime = delEndTime.toString();
    deleteTagDataStmt.setString(1, stTime);
    deleteTagDataStmt.setString(2, endTime);
    for (int i = 0; i < delURIsSize; i++) {
      deleteTagDataStmt.setLong(i + 1 + 2, compactedURIs.get(i));
    }

    if(log.isDebugEnabled()) {
      log.debug("Delete Statement: " + deleteTagDataStmt);
    }

    int numRowsDeleted = deleteTagDataStmt.executeUpdate();
    if (log.isDebugEnabled()) {
      log.debug("Deleted [" + numRowsDeleted + "] Tag Data records for ["
              + delURIsSize + "] tags [" + compactedURIs.toString() + "] in " + (System.currentTimeMillis() - start) + " ms.");
    }
    return numRowsDeleted;
  }

  private long convertToUTC(long ts) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(ts);
    // System.out.println("c.getTime() is : "+ c.getTime());
    // System.out.println("long ts is : "+ ts);

    TimeZone z = c.getTimeZone();
    int offset = z.getRawOffset();
    if (z.inDaylightTime(new Date(ts))) {
      offset = offset + z.getDSTSavings();
    }
    int offsetHrs = offset / 1000 / 60 / 60;
    int offsetMins = offset / 1000 / 60 % 60;

    // System.out.println("offset: " + offsetHrs);
    // System.out.println("offset: " + offsetMins);

    c.add(Calendar.HOUR_OF_DAY, (-offsetHrs));
    c.add(Calendar.MINUTE, (-offsetMins));

    // System.out.println("GMT Time: "+c.getTime() + " ; long-> " + c.getTimeInMillis());
    return c.getTimeInMillis();
  }

  public String getDataType(long uri) throws SQLException {
    // select from TAG_LIST where uri
    String uriDataType = null;
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }

    PreparedStatement queryTagDataDataTypeStmt = null;
    //if (queryTagDataDataTypeStmt == null) {
      queryTagDataDataTypeStmt = getDbConnection()
          .prepareStatement("SELECT datatype FROM " + tagListTable + " where id = ?");
    //} else {
      //queryTagDataDataTypeStmt.clearParameters();
    //}

    long start = System.currentTimeMillis();
    queryTagDataDataTypeStmt.setLong(1, uri);
    ResultSet results = queryTagDataDataTypeStmt.executeQuery();
    if (!results.next()) {
      return null;
    }
    uriDataType = results.getString(1);

    if (log.isDebugEnabled()) {
      log.debug("Queried datatype for uri: " + (System.currentTimeMillis() - start) + "ms. : ");
    }
    return uriDataType;
  }

  public void dropTable(String uncompactedTable) throws SQLException {
    Statement stmt = null;
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }
    if (uncompactedTable == null) {
      throw new IllegalArgumentException("Table name can't be null ");
    }
    stmt = dbConnection.createStatement();
    stmt.executeUpdate("DROP TABLE IF EXISTS " + uncompactedTable);
  }

  public void createTable(String uncompactedTable) throws SQLException {
    Statement stmt = null;
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }
    if (uncompactedTable == null) {
      throw new IllegalArgumentException("Table name can't be null ");
    }
    stmt = dbConnection.createStatement();
    stmt.executeUpdate("CREATE TABLE " + uncompactedTable
        + " (id BIGINT NOT NULL, ts DATE NOT NULL, vl BIGINT, vd DOUBLE, vs VARCHAR, q SMALLINT CONSTRAINT pk PRIMARY KEY (id, ts ROW_TIMESTAMP)) COMPRESSION = 'SNAPPY'");
  }

  public void upsertUncompactedData(String tableName, List<TagData> tduList) throws SQLException {
    if (!hasConnection()) {
      throw new IllegalStateException("no connection");
    }

    if (tableName == null) {
      throw new IllegalArgumentException("td");
    }

    if (upsertTduStmt != null) upsertTduStmt.clearParameters();

    int numRowsUpserted = 0;
    long start = System.currentTimeMillis();

    log.debug("list size is " + tduList.size());
    for (TagData td : tduList) {
      if (td == null) {
        throw new IllegalArgumentException("td");
      }
      upsertTduStmt = getDbConnection().prepareStatement(
        "UPSERT INTO " + tableName + " (id, ts, vl, vd, vs, q) " + " VALUES(?, ?, ?, ?, ?, ?)");
      upsertTduStmt.setLong(1, td.getUri());
      upsertTduStmt.setDate(2, new Date(td.getTs().getTime()));
      upsertTduStmt.setLong(3, td.getVl());
      upsertTduStmt.setDouble(4, td.getVd());
      upsertTduStmt.setString(5, td.getVs());
      upsertTduStmt.setShort(6, td.getQ());
      numRowsUpserted += upsertTduStmt.executeUpdate();
    }

    dbConnection.commit();
    if (log.isDebugEnabled()) {
      log.info("Upserted [" + numRowsUpserted + "] Tag Data Compacted records. Completed  in "
          + (System.currentTimeMillis() - start) + "ms.");
    }
    dbConnection.setAutoCommit(true);
  }
  
}
