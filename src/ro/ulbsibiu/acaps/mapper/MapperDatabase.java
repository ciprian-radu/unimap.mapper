package ro.ulbsibiu.acaps.mapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

/**
 * Singleton that allows connectivity with the MAPPER SQLite database.
 * 
 * @author cipi
 * 
 */
public class MapperDatabase {

	private static final String JDBC_DRIVER = "org.sqlite.JDBC";

	/** default database URL */
	public static final String URL = "jdbc:sqlite:data.db";

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(MapperDatabase.class);

	private static MapperDatabase instance = null;

	private Connection connection = null;

	private String url = URL;

	private String usedId;

	private String password;

	/**
	 * a unique ID, valid only until the database connection is closed.
	 * Typically, this value will identify a run of a mapper algorithm
	 */
	private int run;

	private MapperDatabase() {
		;
	}

	public static final synchronized MapperDatabase getInstance() {
		if (instance == null) {
			instance = new MapperDatabase();
		}
		return instance;
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
			try {
				logger.info("Creating database connection");
				String className = JDBC_DRIVER;
				Class<?> driverObject = Class.forName(className);
				logger.debug("Database driver : " + driverObject);
				logger.debug("Database installation successful");

				connection = DriverManager.getConnection(url, userId, password);
				Statement statement = connection.createStatement();
				statement.execute("PRAGMA foreign_keys = ON;");
				statement
						.executeUpdate("INSERT INTO RUN SELECT MAX(ID) + 1 FROM RUN");
				ResultSet resultSet = statement
						.executeQuery("SELECT MAX(ID) FROM RUN");
				while (resultSet.next()) {
					run = resultSet.getInt(1);
					logger.debug("Run ID is " + run);
				}
			} catch (Exception e) {
				logger.error("Driver installation failed!", e);
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
				this.url = URL;
				this.usedId = null;
				this.password = null;
				logger.info("Database connection closed by user");
			} catch (SQLException e) {
				logger.error(e);
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
			try {
				Statement statement = getConnection().createStatement();
				statement.execute("PRAGMA foreign_keys = ON;");
				statement
						.executeUpdate("INSERT INTO RUN SELECT MAX(ID) + 1 FROM RUN");
				ResultSet resultSet = statement
						.executeQuery("SELECT MAX(ID) FROM RUN");
				while (resultSet.next()) {
					run = resultSet.getInt(1);
					logger.debug("Run ID is " + run);
				}
			} catch (SQLException e) {
				logger.error(e);
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

		Statement statement;
		try {
			statement = getConnection().createStatement();
			ResultSet resultSet = statement
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
				statement
						.executeUpdate("INSERT INTO BENCHMARK (NAME, CTG_ID) VALUES ('"
								+ benchmarkName + "', '" + ctgId + "')");
				resultSet = statement
						.executeQuery("SELECT ID FROM BENCHMARK WHERE NAME = '"
								+ benchmarkName + "' AND CTG_ID = '" + ctgId
								+ "'");
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
		try {
			Statement statement = getConnection().createStatement();
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
		} catch (SQLException e) {
			logger.error(e);
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

		Statement statement;
		try {
			statement = getConnection().createStatement();
			ResultSet resultSet = statement
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
		try {
			Statement statement = getConnection().createStatement();
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
		}
	}

	public void saveMapping(String mapperName, String mapperDescription,
			int benchmarkId, String apcgId, int nocTopologyId,
			String mappingXml, Date startTime, double realTime,
			double userTime, double sysTime, double memoryStart, double memoryEnd) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat();
			sdf.applyPattern("yyyy-MM-dd HH:mm:ss");
			String startTimeAsString = sdf.format(startTime);

			Statement statement = getConnection().createStatement();
			statement
					.executeUpdate("INSERT INTO MAPPER (NAME, DESCRIPTION, BENCHMARK, APCG_ID, NOC_TOPOLOGY, MAPPING_XML, START_DATETIME, REAL_TIME, USER_TIME, SYS_TIME, MEMORY_START, MEMORY_END, RUN) VALUES ('"
							+ mapperName
							+ "', '"
							+ mapperDescription
							+ "', "
							+ benchmarkId
							+ ", '"
							+ apcgId
							+ "', "
							+ nocTopologyId
							+ ", '"
							+ mappingXml
							+ "', strftime('%s','"
							+ startTimeAsString
							+ "'), "
							+ realTime
							+ ", "
							+ userTime
							+ ", "
							+ sysTime
							+ ", "
							+ memoryStart
							+ ", "
							+ memoryEnd
							+ ", "
							+ run + ")");
		} catch (SQLException e) {
			logger.error(e);
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
