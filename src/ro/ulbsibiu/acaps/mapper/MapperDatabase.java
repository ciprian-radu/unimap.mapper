package ro.ulbsibiu.acaps.mapper;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;

/**
 * Singleton that allows connectivity with the UniMap MySQL database.
 * <p>
 * <b>Note: </b>Any failed database operation leads to stopping the program that
 * uses this class.
 * </p>
 * 
 * @author cipi
 * 
 */
public class MapperDatabase {

	private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(MapperDatabase.class);

	private static MapperDatabase instance = null;

	private BasicDataSource basicDataSource;
	
	/** whether or not the database is used */
	private boolean use = false;
	
	private String url = null;

	private String userId = null;

	private String password = null;

	/**
	 * a unique ID, valid only until the database connection is closed.
	 * Typically, this value will identify a run of a mapper algorithm
	 */
	private int run;

	private MapperDatabase() {
		setDefaultDatabaseCredentials();
	}

	public static final synchronized MapperDatabase getInstance() {
		if (instance == null) {
			instance = new MapperDatabase();
		}
		return instance;
	}

	private void setDefaultDatabaseCredentials() {
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream("mysql.properties"));
			this.use = Boolean.parseBoolean(properties.getProperty("database.use"));
			if (!use) {
				logger.warn("The database will not be used (change the 'database.use' property to true if you want to use the database)");
			}
			this.url = properties.getProperty("database.url");
			this.userId = properties.getProperty("database.user");;
			this.password = properties.getProperty("database.password");
		} catch (FileNotFoundException e) {
			logger.error("Couldn't set the default database credentials", e);
		} catch (IOException e) {
			logger.error("Couldn't set the default database credentials", e);
		}
	}
	
	/**
	 * Creates a database connection
	 * 
	 * @param url
	 *            the connection URL (you may use the default {@link #URL})
	 * @param userId
	 *            the user ID
	 * @param password
	 *            the password
	 */
	private synchronized void connect(String url, String userId, String password) {
		this.url = url;
		this.userId = userId;
		this.password = password;
		Statement statement = null;
		ResultSet resultSet = null;
		Connection connection = null;
		try {
			logger.info("Creating database connection");

			if (basicDataSource == null) {
				basicDataSource = new BasicDataSource();
			}
			basicDataSource.setDriverClassName(JDBC_DRIVER);
			basicDataSource.setUsername(this.userId);
			basicDataSource.setPassword(this.password);
			basicDataSource.setUrl(this.url);
			basicDataSource.setMinEvictableIdleTimeMillis(1000 * 60 * 60 * 4); // 4 hours (MySQL has a default wait_timeout of 8 hours)
			basicDataSource.setTimeBetweenEvictionRunsMillis(1000 * 60 * 60 * 4);
			basicDataSource.setNumTestsPerEvictionRun(basicDataSource.getMaxIdle());
			basicDataSource.setValidationQuery("SELECT 1");
			basicDataSource.setTestOnBorrow(true);
			basicDataSource.setTestWhileIdle(true);
			basicDataSource.setTestOnReturn(true);
			
			connection = basicDataSource.getConnection();
			logger.debug("Database installation successful");
			statement = connection.createStatement();
			statement
					.executeUpdate("INSERT INTO RUN SELECT MAX(ID) + 1 FROM RUN", Statement.RETURN_GENERATED_KEYS);
			resultSet = statement.getGeneratedKeys();
			while (resultSet.next()) {
				run = resultSet.getInt(1);
				logger.debug("Run ID is " + run);
			}
		} catch (SQLException e) {
			logger.error("Driver installation failed!", e);
			System.exit(0);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}
	}

	/**
	 * @return the database connection, or <tt>null</tt> if the database is not
	 *         used
	 * @throws SQLException
	 */
	private synchronized Connection getConnection() throws SQLException {
		Connection connection = null;
		if (use) {
			if (basicDataSource == null) {
				connect(url, userId, password);
			}
			connection = basicDataSource.getConnection();
		}
		
		return connection;
	}
	
	/**
	 * Closes the database pool connections
	 */
	public synchronized void close() {
		if (basicDataSource != null) {
			try {
				basicDataSource.close();
				logger.debug("Closed database pool connections");
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}
	}

	/**
	 * @return a unique ID, valid only until the database connection is closed.
	 *         Typically, this value will identify a run of a mapper algorithm
	 */
	public int getRun() {
		return run;
	}

	/**
	 * Increments the run ID. <b>A database connection is opened if a connection
	 * is not already open.</b>
	 */
	public void incrementRun() {
		Connection connection = null;
		Statement statement = null;
		ResultSet resultSet = null;
		try {
			connection = getConnection();
			if (connection != null) {
				statement = connection.createStatement();
				statement
						.executeUpdate("INSERT INTO RUN SELECT MAX(ID) + 1 FROM RUN", Statement.RETURN_GENERATED_KEYS);
				resultSet = statement.getGeneratedKeys();
				while (resultSet.next()) {
					run = resultSet.getInt(1);
					logger.debug("Run ID is " + run);
				}
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} else {
				logger.warn("Calling this method is pointless because no database connection is used!");
			}
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}
	}

	/**
	 * Finds the ID of the specified benchmark. If no ID is found, the benchmark
	 * is automatically inserted into the database.
	 * 
	 * @param benchmarkName
	 *            the benchmark name
	 * @param ctgId
	 *            the CTG ID
	 * @return the benchmark ID
	 */
	public Integer getBenchmarkId(String benchmarkName, String ctgId) {
		Integer id = null;

		Connection connection = null;
		Statement statement = null;
		ResultSet resultSet = null;
		try {
			connection = getConnection();
			if (connection != null) {
				statement = connection.createStatement();
				resultSet = statement
						.executeQuery("SELECT ID FROM BENCHMARK WHERE NAME = '"
								+ benchmarkName + "' AND CTG_ID = '" + ctgId + "'");
				while (resultSet.next()) {
					if (id != null) {
						throw new SQLException("Multiple IDs returned");
					}
					id = resultSet.getInt(1);
				}
				if (id == null) {
					logger.warn("No ID found. Inserting this benchmark CTG into the database");
					statement.executeUpdate("INSERT INTO BENCHMARK (NAME, CTG_ID) VALUES ('"
									+ benchmarkName + "', '" + ctgId + "')", Statement.RETURN_GENERATED_KEYS);
					resultSet = statement.getGeneratedKeys();
					while (resultSet.next()) {
						if (id != null) {
							throw new SQLException("Multiple IDs returned");
						}
						id = resultSet.getInt(1);
					}
					logger.assertLog(id != null, "No ID found");
				}
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} else {
				logger.warn("Calling this method is pointless because no database connection is used!");
			}
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}

		return id;
	}

	/**
	 * Sets the parameters of the mapper, along with their values.
	 * 
	 * @param parameters
	 *            the parameters (cannot be null)
	 * @param values
	 *            the parameter values (cannot be null)
	 */
	public void setParameters(String[] parameters, String[] values) {
		logger.assertLog(parameters != null, "No parameters specified");
		logger.assertLog(values != null, "No values specified");
		logger.assertLog(parameters.length == values.length,
				"The number of parameters, " + parameters.length
						+ ", doesn't match the number of values: "
						+ values.length);
		Connection connection = null;
		Statement statement = null;
		try {
			connection = getConnection();
			if (connection != null) {
				statement = connection.createStatement();
				for (int i = 0; i < parameters.length; i++) {
					statement
							.addBatch("INSERT INTO PARAMETER (ID, NAME, VALUE) VALUES ("
									+ run
									+ ", '"
									+ parameters[i]
									+ "', '"
									+ values[i] + "')");
				}
				statement.executeBatch();
				if (statement != null) {
					statement.close();
				}
			} else {
				logger.warn("Calling this method is pointless because no database connection is used!");
			}
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}
	}

	/**
	 * Finds the ID of the specified NoC topology. If no ID is found, the
	 * topology is automatically inserted into the database.
	 * 
	 * @param topologyName
	 *            the topology's name
	 * @param topologySize
	 *            the size of the topology
	 * @return
	 */
	public Integer getNocTopologyId(String topologyName, String topologySize) {
		Integer id = null;

		Connection connection = null;
		Statement statement = null;
		ResultSet resultSet = null;
		try {
			connection = getConnection();
			if (connection != null) {
				statement = connection.createStatement();
				resultSet = statement
						.executeQuery("SELECT ID FROM NOC_TOPOLOGY WHERE NAME = '"
								+ topologyName + "' AND SIZE = '" + topologySize
								+ "'");
				while (resultSet.next()) {
					if (id != null) {
						throw new SQLException("Multiple IDs returned");
					}
					id = resultSet.getInt(1);
				}
				if (id == null) {
					logger.warn("No ID found. Inserting this topology into the database");
					statement
							.executeUpdate("INSERT INTO NOC_TOPOLOGY (NAME, SIZE) VALUES ('"
									+ topologyName + "', '" + topologySize + "')");
					resultSet = statement
							.executeQuery("SELECT ID FROM NOC_TOPOLOGY WHERE NAME = '"
									+ topologyName
									+ "' AND SIZE = '"
									+ topologySize + "'");
					while (resultSet.next()) {
						if (id != null) {
							throw new SQLException("Multiple IDs returned");
						}
						id = resultSet.getInt(1);
					}
					logger.assertLog(id != null, "No ID found");
				}
			} else {
				logger.warn("Calling this method is pointless because no database connection is used!");
			}
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}

		return id;
	}

	/**
	 * Sets the outputs of the mapper, along with their values.
	 * 
	 * @param outputs
	 *            the output names (cannot be null)
	 * @param values
	 *            the output values (cannot be null)
	 */
	public void setOutputs(String[] outputs, String[] values) {
		logger.assertLog(outputs != null, "No outputs specified");
		logger.assertLog(values != null, "No values specified");
		logger.assertLog(outputs.length == values.length,
				"The number of outputs, " + outputs.length
						+ ", doesn't match the number of values: "
						+ values.length);
		Connection connection = null;
		Statement statement = null;
		try {
			connection = getConnection();
			if (connection != null) {
				statement = connection.createStatement();
				for (int i = 0; i < outputs.length; i++) {
					statement
							.addBatch("INSERT INTO OUTPUT (ID, NAME, VALUE) VALUES ("
									+ run
									+ ", '"
									+ outputs[i]
									+ "', '"
									+ values[i]
									+ "')");
				}
				statement.executeBatch();
			} else {
				logger.warn("Calling this method is pointless because no database connection is used!");
			}
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}
	}

	public void saveMapping(String mapperName, String mapperDescription,
			Integer benchmarkId, String apcgId, Integer nocTopologyId,
			String mappingXml, Date startTime, double realTime,
			double userTime, double sysTime, double averageHeapMemory,
			byte[] averageHeapMemoryChart) {
		Connection connection = null;
		PreparedStatement statement = null;
		try {
			SimpleDateFormat sdf = new SimpleDateFormat();
			sdf.applyPattern("yyyy-MM-dd HH:mm:ss");
			String startTimeAsString = sdf.format(startTime);

			connection = getConnection();
			if (connection != null) {
				statement = connection
						.prepareStatement(
								"INSERT INTO MAPPER (" +
								"NAME, " +
								"DESCRIPTION, " +
								"BENCHMARK, " +
								"APCG_ID, " +
								"NOC_TOPOLOGY, " +
								"MAPPING_XML, " +
								"START_DATETIME, " +
								"REAL_TIME, " +
								"USER_TIME, " +
								"SYS_TIME, " +
								"AVG_HEAP_MEMORY, " +
								"AVG_HEAP_MEMORY_CHART, " +
								"RUN" +
								") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
				statement.setString(1, mapperName);
				statement.setString(2, mapperDescription);
				statement.setInt(3, benchmarkId);
				statement.setString(4, apcgId);
				statement.setInt(5, nocTopologyId);
				statement.setString(6, mappingXml);
				statement.setString(7, startTimeAsString);
				statement.setDouble(8, realTime);
				statement.setDouble(9, userTime);
				statement.setDouble(10, sysTime);
				statement.setDouble(11, averageHeapMemory);
				statement.setBytes(12, averageHeapMemoryChart);
				statement.setInt(13, run);
				
				statement.execute();
			} else {
				logger.warn("Calling this method is pointless because no database connection is used!");
			}
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}
	}

	public static void main(String[] args) throws SQLException {
		// System.out.println(MapperDatabase.getInstance().getBenchmarkId(
		// "auto-indust-mocsyn", "2"));

		// MapperDatabase.getInstance().setParameters(
		// new String[] { "energy", "bandwidth" },
		// new String[] { "1.3e2", "10" });

		// System.out.println(MapperDatabase.getInstance().getNocTopologyId(
		// "2D mesh", "4x4"));

		// MapperDatabase.getInstance().setOutputs(
		// new String[] { "total energy", },
		// new String[] { "1.243e6" });

	}
}
