package ro.ulbsibiu.acaps.mapper;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * Singleton that allows connectivity with the MAPPER SQLite database.
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

	private Connection connection = null;

	private String url = null;

	private String usedId = null;

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
			this.url = properties.getProperty("database.url");
			this.usedId = properties.getProperty("database.user");;
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
	 * @return
	 */
	public Connection connect(String url, String userId, String password) {
		if (connection == null) {
			this.url = url;
			this.usedId = userId;
			this.password = password;
			Statement statement = null;
			ResultSet resultSet = null;
			try {
				logger.info("Creating database connection");
				String className = JDBC_DRIVER;
				Class<?> driverObject = Class.forName(className);
				logger.debug("Database driver : " + driverObject);
				logger.debug("Database installation successful");

				connection = DriverManager.getConnection(url, userId, password);
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
			} catch (ClassNotFoundException e) {
				logger.error("JDBC driver class not found!", e);
				System.exit(0);
			} finally {
				try {
					if (resultSet != null) {
						resultSet.close();
					}
					if (statement != null) {
						statement.close();
					}
				} catch (SQLException e) {
					logger.error(e);
					System.exit(0);
				}
			}
		} else {
			logger.info("Using already existing database connection. "
					+ "If you want to connect to a different URL or with different credentials, close the exiting connection!");
		}
		return connection;
	}

	public void closeConnection() {
		if (connection == null) {
			logger.warn("No connection to close; nothing to do");
		} else {
			try {
				logger.debug("Closing database connection...");
				connection.close();
				connection = null;
				logger.info("Database connection closed by user");
				setDefaultDatabaseCredentials();
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}
	}

	/**
	 * @return the database connection
	 */
	public Connection getConnection() {
		if (connection == null) {
			logger.warn("There is no database connection created. Connecting to the database using defaults");
			connect(url, usedId, password);
		}
		return connection;
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
		if (connection == null) {
			getConnection();
		} else {
			Statement statement = null;
			ResultSet resultSet = null;
			try {
				statement = getConnection().createStatement();
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
				} catch (SQLException e) {
					logger.error(e);
					System.exit(0);
				}
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
	public int getBenchmarkId(String benchmarkName, String ctgId) {
		Integer id = null;

		Statement statement = null;
		ResultSet resultSet = null;
		try {
			statement = getConnection().createStatement();
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
		Statement statement = null;
		try {
			statement = getConnection().createStatement();
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
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (statement != null) {
					statement.close();
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
	public int getNocTopologyId(String topologyName, String topologySize) {
		Integer id = null;

		Statement statement = null;
		ResultSet resultSet = null;
		try {
			statement = getConnection().createStatement();
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
		Statement statement = null;
		try {
			statement = getConnection().createStatement();
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
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (SQLException e) {
				logger.error(e);
				System.exit(0);
			}
		}
	}

	public void saveMapping(String mapperName, String mapperDescription,
			int benchmarkId, String apcgId, int nocTopologyId,
			String mappingXml, Date startTime, double realTime,
			double userTime, double sysTime, double averageHeapMemory,
			byte[] averageHeapMemoryChart) {
		PreparedStatement statement = null;
		try {
			SimpleDateFormat sdf = new SimpleDateFormat();
			sdf.applyPattern("yyyy-MM-dd HH:mm:ss");
			String startTimeAsString = sdf.format(startTime);

			statement = getConnection()
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
		} catch (SQLException e) {
			logger.error(e);
			System.exit(0);
		} finally {
			try {
				if (statement != null) {
					statement.close();
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
