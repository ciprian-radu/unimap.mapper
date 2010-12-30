package ro.ulbsibiu.acaps.mapper.sa.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.CoreType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.TaskType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CommunicationType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MapType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MappingType;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.MapperDatabase;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.sa.Core;
import ro.ulbsibiu.acaps.mapper.sa.SimulatedAnnealingMapper;
import ro.ulbsibiu.acaps.mapper.util.HeapUsageMonitor;
import ro.ulbsibiu.acaps.mapper.util.MapperInputProcessor;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.mapper.util.TimeUtils;
import ro.ulbsibiu.acaps.noc.xml.link.LinkType;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;
import ro.ulbsibiu.acaps.noc.xml.node.ObjectFactory;
import ro.ulbsibiu.acaps.noc.xml.node.RoutingTableEntryType;
import ro.ulbsibiu.acaps.noc.xml.node.TopologyParameterType;

/**
 * This is a test version of Simulated Annealing algorithm for Network-on-Chip
 * (NoC) application mapping. The implementation is based on the one from <a
 * href="http://www.ece.cmu.edu/~sld/wiki/doku.php?id=shared:nocmap">NoCMap</a>
 * 
 * <p>
 * Note that currently, this algorithm works only with N x N 2D mesh NoCs
 * </p>
 * 
 * @see SimulatedAnnealingMapper
 * 
 * @author cipi
 * 
 */
public class SimulatedAnnealingTestMapper implements Mapper {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(SimulatedAnnealingTestMapper.class);
	
	private static final String MAPPER_ID = "sa_test";

	private static final int NORTH = 0;

	private static final int SOUTH = 1;

	private static final int EAST = 2;

	private static final int WEST = 3;

	/**
	 * how costly is each unit of link overload (a link is overloaded when it
	 * has to send more bits/s than its bandwidth)
	 */
	private final float OVERLOAD_UNIT_COST = 1000000000;

	/**
	 * whether or not to build routing table too. When the SA algorithm builds
	 * the routing table, the mapping process takes more time but, this should
	 * yield better performance
	 */
	private boolean buildRoutingTable;

	/**
	 * When the algorithm builds the routing table, it avoids deadlocks by
	 * employing a set of legal turns.
	 * 
	 * @author cipi
	 * 
	 */
	public enum LegalTurnSet {
		WEST_FIRST, ODD_EVEN
	}

	/**
	 * what {@link LegalTurnSet} the SA algorithm should use (this is useful
	 * only when the routing table is built)
	 */
	private LegalTurnSet legalTurnSet;

	/** the link bandwidth */
	private double linkBandwidth;
	
	/** energy consumption per bit read */
	private float bufReadEBit;

	/** energy consumption per bit write */
	private float bufWriteEBit;

	/** the number of nodes (nodes) from the NoC */
	private int nodesNumber;

	/**
	 * the size of the 2D mesh, sqrt(nodesNumber) (sqrt(nodesNumber) * sqrt(nodesNumber)
	 * = nodesNumber)
	 */
	private int edgeSize;

	/**
	 * the number of processes (tasks). Note that each core has only one task
	 * associated to it.
	 */
	private int coresNumber;
	
	/** counts how many cores were parsed from the parsed APCGs */
	private int previousCoreCount = 0;

	/**
	 * the number of links from the NoC
	 */
	private int linksNumber;

	/** the nodes from the Network-on-Chip (NoC) */
	private NodeType[] nodes;
	
	/** the distinct nodes with which each node communicates directly (through a single link) */
	private Set<Integer>[] nodeNeighbors;
	
	/** the maximum neighbors a node can have */
	private int maxNodeNeighbors = 0;

	/** the processes (tasks, cores) */
	private Core[] cores;
	
	/** the distinct cores with which each core communicates directly */
	private Set<Integer>[] coreNeighbors;

	/** the communication channels from the NoC */
	private ro.ulbsibiu.acaps.noc.xml.link.LinkType[] links;

	/**
	 * what links are used by nodes to communicate (each source - destination
	 * node pair has a list of link IDs). The matrix must have size
	 * <tt>nodesNumber x nodesNumber</tt>. <b>This must be <tt>null</tt> when
	 * <tt>buildRoutingTable</tt> is <tt>true</tt> </b>
	 */
	private List<Integer>[][] linkUsageList = null;

	/** the seed for the random number generator */
	private int seed;

	/**
	 * how many mapping attempts the algorithm tries per iteration. A mapping
	 * attempt means a random swap of processes (tasks) between to network nodes
	 */
	private int numberOfIterationsPerTemperature;
	
	private double temperature;

	/** how many consecutive moves were rejected at a certain temperature level */
	private int numberOfConsecutiveRejectedMoves;

	/** the cost of the initial mapping */
	private double initialCost;
	
	/** the cost of the current mapping */
	private double currentCost;

	/** the acceptance ratio */
	private double acceptRatio;

	/**
	 * per link bandwidth usage (used only when the algorithm doesn't build the
	 * routing table)
	 */
	private int[] linkBandwidthUsage = null;

	/**
	 * per link bandwidth usage (used only when the algorithm builds the routing
	 * table)
	 */
	private int[][][] synLinkBandwithUsage = null;

	/** holds the generated routing table */
	private int[][][][] saRoutingTable = null;

	/** the benchmark's name */
	private String benchmarkName;
	
	/** the CTG ID */
	private String ctgId;
	
	/** the ACPG ID */
	private String apcgId;
	
	/** the directory where the NoC topology is described */
	private File topologyDir;
	
	/** the topology name */
	private String topologyName;
	
	/** the topology size */
	private String topologySize;
	
	private String[] bestSolution;
	
	private double bestCost = Float.MAX_VALUE;
	
	private double bestSolutionTemperature;
	
	private int bestSolutionIteration;
	
	private int mappingIteration;
	
	private static enum TopologyParameter {
		/** on what row of a 2D mesh the node is located */
		ROW,
		/** on what column of a 2D mesh the node is located */
		COLUMN,
	};
	
	private static final String LINK_IN = "in";
	
	private static final String LINK_OUT = "out";
	
	private Integer[] nodeRows;
	
	private Integer[] nodeColumns;
	
	private String getNodeTopologyParameter(NodeType node,
			TopologyParameter parameter) {
		String value = null;
		if (TopologyParameter.ROW.equals(parameter)
				&& nodeRows[Integer.valueOf(node.getId())] != null) {
			value = Integer.toString(nodeRows[Integer.valueOf(node.getId())]);
		} else {
			if (TopologyParameter.COLUMN.equals(parameter)
					&& nodeColumns[Integer.valueOf(node.getId())] != null) {
				value = Integer.toString(nodeColumns[Integer.valueOf(node
						.getId())]);
			} else {
				List<TopologyParameterType> topologyParameters = node
						.getTopologyParameter();
				for (int i = 0; i < topologyParameters.size(); i++) {
					if (parameter.toString().equalsIgnoreCase(
							topologyParameters.get(i).getType())) {
						value = topologyParameters.get(i).getValue();
						break;
					}
				}
				logger.assertLog(value != null,
						"Couldn't find the topology parameter '" + parameter
								+ "' in the node " + node.getId());

				if (TopologyParameter.ROW.equals(parameter)) {
					nodeRows[Integer.valueOf(node.getId())] = Integer
							.valueOf(value);
				}
				if (TopologyParameter.COLUMN.equals(parameter)) {
					nodeColumns[Integer.valueOf(node.getId())] = Integer
							.valueOf(value);
				}
			}
		}

		return value;
	}
	
	/** routingTables[nodeId][sourceNode][destinationNode] = link ID */
	private int[][][] routingTables;
	
	public void generateXYRoutingTable() {
		for (int n = 0; n < nodes.length; n++) {
			NodeType node = nodes[n];
			for (int i = 0; i < nodesNumber; i++) {
				for (int j = 0; j < nodesNumber; j++) {
					routingTables[Integer.valueOf(node.getId())][i][j] = -2;
				}
			}
	
			for (int dstNode = 0; dstNode < nodesNumber; dstNode++) {
				if (dstNode == Integer.valueOf(node.getId())) { // deliver to me
					routingTables[Integer.valueOf(node.getId())][0][dstNode] = -1;
				} else {
					// check out the dst Node's position first
					int dstRow = dstNode / edgeSize;
					int dstCol = dstNode % edgeSize;
		
					int row = Integer.valueOf(getNodeTopologyParameter(node, TopologyParameter.ROW));
					int column = Integer.valueOf(getNodeTopologyParameter(node, TopologyParameter.COLUMN));
					int nextStepRow = row;
					int nextStepCol = column;
		
					if (dstCol != column) { // We should go horizontally
						if (column > dstCol) {
							nextStepCol--;
						} else {
							nextStepCol++;
						}
					} else { // We should go vertically
						if (row > dstRow) {
							nextStepRow--;
						} else {
							nextStepRow++;
						}
					}
		
					for (int i = 0; i < node.getLink().size(); i++) {
						if (LINK_OUT.equals(node.getLink().get(i).getType())) {
							String nodeRow = "-1";
							String nodeColumn = "-1";
							// the links are bidirectional
							if (links[Integer.valueOf(node.getLink().get(i).getValue())].getFirstNode().equals(node.getId())) {
								nodeRow = getNodeTopologyParameter(
										nodes[Integer.valueOf(links[Integer.valueOf(node.getLink().get(i).getValue())].getSecondNode())],
										TopologyParameter.ROW);
								nodeColumn = getNodeTopologyParameter(
										nodes[Integer.valueOf(links[Integer.valueOf(node.getLink().get(i).getValue())].getSecondNode())],
										TopologyParameter.COLUMN);
							} else {
								if (links[Integer.valueOf(node.getLink().get(i).getValue())].getSecondNode().equals(node.getId())) {
									nodeRow = getNodeTopologyParameter(
											nodes[Integer.valueOf(links[Integer.valueOf(node.getLink().get(i).getValue())].getFirstNode())],
											TopologyParameter.ROW);
									nodeColumn = getNodeTopologyParameter(
											nodes[Integer.valueOf(links[Integer.valueOf(node.getLink().get(i).getValue())].getFirstNode())],
											TopologyParameter.COLUMN);
								}
							}
							if (Integer.valueOf(nodeRow) == nextStepRow
									&& Integer.valueOf(nodeColumn) == nextStepCol) {
								routingTables[Integer.valueOf(node.getId())][0][dstNode] = Integer.valueOf(links[Integer
										.valueOf(node.getLink().get(i).getValue())]
										.getId());
								break;
							}
						}
					}
				}
			}
	
			// Duplicate this routing row to the other routing rows.
			for (int i = 1; i < nodesNumber; i++) {
				for (int j = 0; j < nodesNumber; j++) {
					routingTables[Integer.valueOf(node.getId())][i][j] = routingTables[Integer.valueOf(node.getId())][0][j];
				}
			}
		}
	}

	/**
	 * Default constructor
	 * <p>
	 * No routing table is built.
	 * </p>
	 * 
	 * @param benchmarkName
	 *            the benchmark's name
	 * @param ctgId
	 *            the CTG ID
	 * @param topologyName
	 *            the topology name
	 * @param topologySize
	 *            the topology size
	 * @param topologyDir
	 *            the topology directory is used to initialize the NoC topology
	 *            for XML files. These files are split into two categories:
	 *            nodes and links. The nodes are expected to be located into the
	 *            "nodes" subdirectory, and the links into the "links"
	 *            subdirectory
	 * @param coresNumber
	 *            the number of processes (tasks). Note that each core has only
	 *            one task associated to it
	 * @param linkBandwidth
	 *            the bandwidth of each network link
	 */
	public SimulatedAnnealingTestMapper(String benchmarkName, String ctgId,
			String apcgId, String topologyName, String topologySize,
			File topologyDir, int coresNumber, double linkBandwidth,
			float switchEBit, float linkEBit) throws JAXBException {
		this(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, false,
				LegalTurnSet.WEST_FIRST, 1.056f, 2.831f, switchEBit, linkEBit);
	}

	/**
	 * Constructor
	 * 
	 * @param benchmarkName
	 *            the benchmark's name
	 * @param ctgId
	 *            the CTG ID
	 * @param apcgId
	 *            the APCG ID
	 * @param topologyName
	 *            the topology name
	 * @param topologySize
	 *            the topology size
	 * @param topologyDir
	 *            the topology directory is used to initialize the NoC topology
	 *            for XML files. These files are split into two categories:
	 *            nodes and links. The nodes are expected to be located into the
	 *            "nodes" subdirectory, and the links into the "links"
	 *            subdirectory
	 * @param coresNumber
	 *            the number of processes (tasks). Note that each core has only
	 *            one task associated to it
	 * @param linkBandwidth
	 *            the bandwidth of each network link
	 * @param buildRoutingTable
	 *            whether or not to build routing table too
	 * @param legalTurnSet
	 *            what {@link LegalTurnSet} the SA algorithm should use (this is
	 *            useful only when the routing table is built)
	 * @param bufReadEBit
	 *            energy consumption per bit read
	 * @param bufWriteEBit
	 *            energy consumption per bit write
	 * @param switchEBit
	 *            the energy consumed for switching one bit of data
	 * @param linkEBit
	 *            the energy consumed for sending one data bit
	 * @throws JAXBException
	 */
	public SimulatedAnnealingTestMapper(String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir, int coresNumber,
			double linkBandwidth, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit,
			float switchEBit, float linkEBit) throws JAXBException {
		logger.assertLog(topologyDir != null, "Please specify the NoC topology directory!");
		logger.assertLog(topologyDir.isDirectory(),
				"The specified NoC topology directory does not exist or is not a directory!");
		this.benchmarkName = benchmarkName;
		this.ctgId = ctgId;
		this.apcgId = apcgId;
		this.topologyName = topologyName;
		this.topologySize = topologySize;
		this.topologyDir = topologyDir;
		this.coresNumber = coresNumber;
		this.linkBandwidth = linkBandwidth;
		this.buildRoutingTable = buildRoutingTable;
		this.legalTurnSet = legalTurnSet;
		this.bufReadEBit = bufReadEBit;
		this.bufWriteEBit = bufWriteEBit;

		initializeNocTopology(switchEBit, linkEBit);
		initializeCores();
	}
	
	@Override
	public String getMapperId() {
		return MAPPER_ID;
	}

	private void initializeCores() {
		cores = new Core[coresNumber];
		for (int i = 0; i < cores.length; i++) {
			cores[i] = new Core(i, null, -1);
			cores[i].setFromCommunication(new long[nodesNumber]);
			cores[i].setToCommunication(new long[nodesNumber]);
			cores[i].setFromBandwidthRequirement(new long[nodesNumber]);
			cores[i].setToBandwidthRequirement(new long[nodesNumber]);
		}
	}

	private List<CommunicationType> getCommunications(CtgType ctg, String sourceTaskId) {
		logger.assertLog(ctg != null, null);
		logger.assertLog(sourceTaskId != null, null);
		
		List<CommunicationType> communications = new ArrayList<CommunicationType>();
		List<CommunicationType> communicationTypeList = ctg.getCommunication();
		for (int i = 0; i < communicationTypeList.size(); i++) {
			CommunicationType communicationType = communicationTypeList.get(i);
			if (sourceTaskId.equals(communicationType.getSource().getId())
					|| sourceTaskId.equals(communicationType.getDestination().getId())) {
				communications.add(communicationType);
			}
		}
		
		return communications;
	}

	private String getCoreUid(ApcgType apcg, String sourceTaskId) {
		logger.assertLog(apcg != null, null);
		logger.assertLog(sourceTaskId != null, null);
		
		String coreUid = null;
		
		List<CoreType> cores = apcg.getCore();
		done: for (int i = 0; i < cores.size(); i++) {
			List<TaskType> tasks = cores.get(i).getTask();
			for (int j = 0; j < tasks.size(); j++) {
				if (sourceTaskId.equals(tasks.get(j).getId())) {
					coreUid = cores.get(i).getUid();
					break done;
				}
			}
		}

		return coreUid;
	}
	
	/**
	 * Reads the information from the (Application Characterization Graph) APCG
	 * and its corresponding (Communication Task Graph) CTG. Additionally, it
	 * informs the algorithm about the application's bandwidth requirement. The
	 * bandwidth requirements of the application are expressed as a multiple of
	 * the communication volume. For example, a value of 2 means that each two
	 * communicating IP cores require a bandwidth twice their communication
	 * volume.
	 * 
	 * @param apcg
	 *            the APCG XML
	 * @param ctg
	 *            the CTG XML
	 * @param applicationBandwithRequirement
	 *            the bandwidth requirement of the application
	 */
	public void parseApcg(ApcgType apcg, CtgType ctg, int applicationBandwithRequirement) {
		logger.assertLog(apcg != null, "The APCG cannot be null");
		logger.assertLog(ctg != null, "The CTG cannot be null");
		
		// we use previousCoreCount to shift the cores from each APCG
		int tasks = 0;
		List<CoreType> coreList = apcg.getCore();
		for (int i = 0; i < coreList.size(); i++) {
			CoreType coreType = coreList.get(i);
			List<TaskType> taskList = coreType.getTask();
			for (int j = 0; j < taskList.size(); j++) {
				TaskType taskType = taskList.get(j);
				String taskId = taskType.getId();
				cores[previousCoreCount + Integer.valueOf(coreType.getUid())].setApcgId(apcg.getId());
				List<CommunicationType> communications = getCommunications(ctg, taskId);
				for (int k = 0; k < communications.size(); k++) {
					CommunicationType communicationType = communications.get(k);
					String sourceId = communicationType.getSource().getId();
					String destinationId = communicationType.getDestination().getId();
					
					String sourceCoreId = null;
					String destinationCoreId = null;
					
					if (taskId.equals(sourceId)) {
						sourceCoreId = getCoreUid(apcg, sourceId);
						destinationCoreId = getCoreUid(apcg, destinationId);
						cores[previousCoreCount + Integer.valueOf(sourceCoreId)].setCoreId(Integer.valueOf(coreType.getUid()));
					}
					if (taskId.equals(destinationId)) {
						sourceCoreId = getCoreUid(apcg, sourceId);
						destinationCoreId = getCoreUid(apcg, destinationId);
						cores[previousCoreCount + Integer.valueOf(destinationCoreId)].setCoreId(Integer.valueOf(coreType.getUid()));
					}
					
					logger.assertLog(sourceCoreId != null, null);
					logger.assertLog(destinationCoreId != null, null);
					
					if (sourceCoreId.equals(destinationCoreId)) {
						logger.warn("Ignoring communication between tasks "
								+ sourceId + " and " + destinationId
								+ " because they are on the same core ("
								+ sourceCoreId + ")");
					} else {
						cores[previousCoreCount + Integer.valueOf(sourceCoreId)]
								.getToCommunication()[previousCoreCount
								+ Integer.valueOf(destinationCoreId)] = (long) communicationType
								.getVolume();
						cores[previousCoreCount + Integer.valueOf(sourceCoreId)]
								.getToBandwidthRequirement()[previousCoreCount
								+ Integer.valueOf(destinationCoreId)] = (long) (applicationBandwithRequirement * communicationType
								.getVolume());
						cores[previousCoreCount
								+ Integer.valueOf(destinationCoreId)]
								.getFromCommunication()[previousCoreCount
								+ Integer.valueOf(sourceCoreId)] = (long) communicationType
								.getVolume();
						cores[previousCoreCount
								+ Integer.valueOf(destinationCoreId)]
								.getFromBandwidthRequirement()[previousCoreCount
								+ Integer.valueOf(sourceCoreId)] = (long) (applicationBandwithRequirement * communicationType
								.getVolume());
					}
				}
			}
			tasks += taskList.size();
		}
		previousCoreCount += tasks;
	}
	
	/**
	 * Initializes the NoC topology for XML files. These files are split into
	 * two categories: nodes and links. The nodes are expected to be located
	 * into the "nodes" subdirectory, and the links into the "links"
	 * subdirectory.
	 * 
	 * @param switchEBit
	 *            the energy consumed for switching a bit of data
	 * @param linkEBit
	 *            the energy consumed for sending a data bit
	 * @throws JAXBException 
	 */
	private void initializeNocTopology(float switchEBit, float linkEBit) throws JAXBException {
		// initialize nodes
		File nodesDir = new File(topologyDir, "nodes");
		logger.assertLog(nodesDir.isDirectory(), nodesDir.getName() + " is not a directory!");
		File[] nodeXmls = nodesDir.listFiles(new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".xml");
			}
		});
		logger.debug("Found " + nodeXmls.length + " nodes");
		this.nodesNumber = nodeXmls.length;
		nodes = new NodeType[nodesNumber];
		nodeNeighbors = new LinkedHashSet[nodesNumber];
		nodeRows = new Integer[nodesNumber];
		nodeColumns = new Integer[nodesNumber];
		this.edgeSize = (int) Math.sqrt(nodesNumber);
		for (int i = 0; i < nodeXmls.length; i++) {
			JAXBContext jaxbContext = JAXBContext
					.newInstance("ro.ulbsibiu.acaps.noc.xml.node");
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			@SuppressWarnings("unchecked")
			NodeType node = ((JAXBElement<NodeType>) unmarshaller
					.unmarshal(nodeXmls[i])).getValue();
			
			node.setCore(Integer.toString(-1));
			node.setCost((double)switchEBit);
			nodes[Integer.valueOf(node.getId())] = node;
		}
		// initialize links
		File linksDir = new File(topologyDir, "links");
		logger.assertLog(linksDir.isDirectory(), linksDir.getName() + " is not a directory!");
		File[] linkXmls = linksDir.listFiles(new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".xml");
			}
		});
		logger.debug("Found " + linkXmls.length + " links");
		this.linksNumber = linkXmls.length;
		links = new LinkType[linksNumber];
		for (int i = 0; i < linkXmls.length; i++) {
			JAXBContext jaxbContext = JAXBContext
					.newInstance("ro.ulbsibiu.acaps.noc.xml.link");
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			@SuppressWarnings("unchecked")
			LinkType link = ((JAXBElement<LinkType>) unmarshaller
					.unmarshal(linkXmls[i])).getValue();
			
			link.setBandwidth(linkBandwidth);
			link.setCost((double)linkEBit);
			links[Integer.valueOf(link.getId())] = link;
			
			if (nodeNeighbors[Integer.valueOf(link.getFirstNode())] == null) {
				nodeNeighbors[Integer.valueOf(link.getFirstNode())] = new LinkedHashSet<Integer>();
			}
			if (nodeNeighbors[Integer.valueOf(link.getSecondNode())] == null) {
				nodeNeighbors[Integer.valueOf(link.getSecondNode())] = new LinkedHashSet<Integer>();
			}
			nodeNeighbors[Integer.valueOf(link.getFirstNode())].add(Integer.valueOf(link.getSecondNode()));
			nodeNeighbors[Integer.valueOf(link.getSecondNode())].add(Integer.valueOf(link.getFirstNode()));
		}
		for (int i = 0; i < nodeNeighbors.length; i++) {
			if (nodeNeighbors[i].size() > maxNodeNeighbors) {
				maxNodeNeighbors = nodeNeighbors[i].size();
			}
		}
		if (logger.isDebugEnabled()) {
			for (int i = 0; i < nodeNeighbors.length; i++) {
				logger.debug("Node " + i + " communicates with " + nodeNeighbors[i].size() + " nodes");
			}
			logger.debug("The maximum nodes a node has is " + maxNodeNeighbors);
		}

		// for each router generate a routing table provided by the XY routing
		// protocol
		routingTables = new int[nodesNumber][nodesNumber][nodesNumber];
		generateXYRoutingTable();

		generateLinkUsageList();
	}

	private void generateLinkUsageList() {
		if (this.buildRoutingTable == true) {
			linkUsageList = null;
		} else {
			// Allocate the space for the link usage table
			int[][][] linkUsageMatrix = new int[nodesNumber][nodesNumber][linksNumber];

			// Setting up the link usage matrix
			for (int srcId = 0; srcId < nodesNumber; srcId++) {
				for (int dstId = 0; dstId < nodesNumber; dstId++) {
					if (srcId == dstId) {
						continue;
					}
					NodeType currentNode = nodes[srcId];
					while (Integer.valueOf(currentNode.getId()) != dstId) {
						int linkId = routingTables[Integer.valueOf(currentNode.getId())][srcId][dstId];
						LinkType link = links[linkId];
						linkUsageMatrix[srcId][dstId][linkId] = 1;
						String node = "-1";
						// we work with with bidirectional links
						if (currentNode.getId().equals(link.getFirstNode())) {
							node = link.getSecondNode();
						} else {
							if (currentNode.getId().equals(link.getSecondNode())) {
								node = link.getFirstNode();
							}
						}
						currentNode = nodes[Integer.valueOf(node)];
					}
				}
			}

			// Now build the link usage list
			linkUsageList = new ArrayList[nodesNumber][nodesNumber];
			for (int src = 0; src < nodesNumber; src++) {
				for (int dst = 0; dst < nodesNumber; dst++) {
					linkUsageList[src][dst] = new ArrayList<Integer>();
					if (src == dst) {
						continue;
					}
					for (int linkId = 0; linkId < linksNumber; linkId++) {
						if (linkUsageMatrix[src][dst][linkId] == 1) {
							linkUsageList[src][dst].add(linkId);
						}
					}
				}
			}

			logger.assertLog(this.linkUsageList != null, null);
			logger.assertLog(linkUsageList.length == nodesNumber, null);
			for (int i = 0; i < linkUsageList.length; i++) {
				logger.assertLog(linkUsageList[i].length == nodesNumber, null);
			}
		}
	}

	private void mapCoresToNocNodesRandomly() {
		Random rand = new Random();
		for (int i = 0; i < coresNumber; i++) {
			int k = Math.abs(rand.nextInt()) % nodesNumber;
			while (Integer.valueOf(nodes[k].getCore()) != -1) {
				k = Math.abs(rand.nextInt()) % nodesNumber;
			}
			cores[i].setNodeId(k);
			nodes[k].setCore(Integer.toString(i));
		}

//		// this maps the cores like NoCMap does
//		int[] coreMap = new int[] { 11, 13, 10, 8, 12, 0, 9, 1, 2, 4, 14, 15,
//				5, 3, 7, 6 };
//		for (int i = 0; i < coresNumber; i++) {
//			cores[i].setNodeId(coreMap[i]);
//			nodes[coreMap[i]].setCore(Integer.toString(i));
//		}
	}

	/**
	 * Prints the current mapping
	 */
	private void printCurrentMapping() {
		for (int i = 0; i < nodesNumber; i++) {
			String apcg = "";
			if (!"-1".equals(nodes[i].getCore())) {
				apcg = cores[Integer.valueOf(nodes[i].getCore())].getApcgId();
			}
			System.out.println("NoC node " + nodes[i].getId() + " has core "
					+ nodes[i].getCore() + " (APCG " + apcg + ")");
		}
	}

	// ways to gen Random Vars with specific distributions
	/**
	 * Simple random number generator based on the linear-congruential method
	 * using parameters from example D, p 40, Knuth Vol 2.
	 * 
	 * @return a real number uniformly distributed on [0,1]. This version has
	 *         the advantage that it should behave the same on different
	 *         machines, since the generator and starting point are explicitly
	 *         specified.
	 */
	private double uniformRandomVariable() {
		// one small problem: the sequence we use can produce integers larger
		// than the word size used, i.e. they can wrap around negative. We wimp
		// out on this matter and just make them positive again.

		final int A = 147453245;
		final int C = 226908347;
		final int M = 1073741824;

		seed = ((A * seed) + C) % M;
		if (seed < 0) {
			seed = -seed;
		}
		double u = (((double) seed) / ((double) M));
		if (logger.isTraceEnabled()) {
			logger.trace(u);
		}
		return u;
	}

	/**
	 * @return a random INTEGER in [imin, imax]
	 */
	private long uniformIntegerRandomVariable(long imin, long imax) {
		double u;
		int m;

		u = uniformRandomVariable();
		m = (int) imin + ((int) Math.floor((double) (imax + 1 - imin) * u));
		if (logger.isTraceEnabled()) {
			logger.trace("Generated integer random number from interval [" + imin
					+ ", " + imax + "] = " + m);
		}
		return m;
	}

	/**
	 * Initialize the random number stream
	 **/
	private void initRand(int seed) {
		this.seed = seed;
	}

	/**
	 * the acceptance function
	 * 
	 * @param deltac
	 *            the <b>normalized</b> cost (energy) variation
	 * 
	 * @return <tt>true</tt> for accept, <tt>false</tt>, otherwise
	 */
	private boolean accept(double deltac) {
		double pa = -1; // probability of acceptance
		boolean accept = false;
		double r = -1;

		if (MathUtils.definitelyLessThan((float) deltac, 0)
				|| MathUtils.approximatelyEqual((float) deltac, 0)) {
			accept = true;
		} else {
//			// normalized exponential form
//			pa = Math.exp((double) (-deltac) / temperature);
			
			// inverse normalized exponential form
			pa = 1 / (1 + Math.exp((double) (deltac) / temperature));
			
			r = uniformRandomVariable();
			if (MathUtils.definitelyLessThan((float) r, (float)pa)
					|| MathUtils.approximatelyEqual((float) r, (float)pa)) {
				accept = true;
			} else {
				accept = false;
			}
		}
		if (logger.isTraceEnabled()) {
			logger.trace("deltac " + deltac + " temp " + temperature + " r " + r
					+ " pa " + pa + " accept " + accept);
		}
		return accept;
	}

	/**
	 * this does the actual evolution of the placement by annealing at a fixed
	 * temperature <tt>t</tt>.
	 */
	private double annealAtTemperature() {
		int acceptCount = 0;
		double totalDeltaCost = 0;
		numberOfConsecutiveRejectedMoves = 0;
		
		int unit = numberOfIterationsPerTemperature / 10;

		// this is the main loop doing moves. We do 'attempts' moves in all,
		// then quit at this temperature

		if (logger.isTraceEnabled()) {
			logger.trace("attempts = " + numberOfIterationsPerTemperature);
		}
//		List<String[]> uniqueMappings = new ArrayList<String[]>(); 
//		List<Integer> uniqueMappingsFrequencies = new ArrayList<Integer>();
		for (int m = 1; m <= numberOfIterationsPerTemperature; m++) {
			int[] movedNodes = move();
			
//			// computes the unique mappings (start)
//			boolean isNewMapping = true;
//			for (int i = 0; i < uniqueMappings.size(); i++) {
//				boolean found = true;
//				logger.assertLog(this.nodes.length == uniqueMappings.get(i).length, null);
//				for (int j = 0; j < uniqueMappings.get(i).length; j++) {
//					if (!uniqueMappings.get(i)[j].equals(this.nodes[j].getCore())) {
//						found = false;
//						break;
//					}
//				}
//				if (found) {
//					isNewMapping = false;
//					uniqueMappingsFrequencies.set(i, uniqueMappingsFrequencies.get(i) + 1);
//				}
//			}
//			if (isNewMapping) {
//				String[] map = new String[this.nodes.length];
//				for (int i = 0; i < this.nodes.length; i++) {
//					map[i] = this.nodes[i].getCore();
//				}
//				uniqueMappings.add(map);
//				uniqueMappingsFrequencies.add(1);
//			}
//			// computes the unique mappings (end)
			
			int node1 = movedNodes[0];
			int node2 = movedNodes[1];

			double newCost = calculateTotalCost();

			double deltaCost = newCost - currentCost;
			if (logger.isTraceEnabled()) {
				logger.trace("deltaCost " + deltaCost + " newCost " + newCost
						+ " currentCost " + currentCost);
			}
			// we normalize deltac
	        double deltac = deltaCost / initialCost;
			// Note that we use machine epsilon to perform the following
			// comparison between the float numbers
	        if (MathUtils.approximatelyEqual((float)deltac, 0)) {
	            deltac = 0;
	        }
			if (MathUtils.definitelyLessThan((float)deltac, 0) || accept(deltac)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Accepting...");
				}
				if (MathUtils.definitelyLessThan((float)newCost, (float)bestCost)) {
					if (logger.isDebugEnabled()) {
						logger.debug("new cost < best cost (" + newCost + " < " + bestCost + ")");
					}
					bestSolutionTemperature = temperature;
					bestSolutionIteration = mappingIteration;
					bestCost = newCost;
					bestSolution = new String[nodes.length];
					for (int i = 0; i < nodes.length; i++) {
						bestSolution[i] = nodes[i].getCore();
					}
					numberOfConsecutiveRejectedMoves = 0;
				} else {
					numberOfConsecutiveRejectedMoves++;
				}
				acceptCount++;
				totalDeltaCost += deltaCost;
				currentCost = newCost;
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("Rolling back nodes " + node1 + " and " + node2);
				}
				swapProcesses(node1, node2); // roll back
				numberOfConsecutiveRejectedMoves++;
			}
			if (m % unit == 0) {
				// This is just to print out the process of the algorithm
				System.out.print("#");
			}
		}
		System.out.println();
		
//		// prints the unique mappings
//		System.out.println("Found " + uniqueMappings.size() + " unique mappings (from a total of " + numberOfIterationsPerTemperature + " mappings)");
////		System.out.println("with the following frequencies:");
////		for (int i = 0; i < uniqueMappings.size(); i++) {
////			if (uniqueMappingsFrequencies.get(i) > 1) {
////				for (int j = 0; j < uniqueMappings.get(i).length; j++) {
////					System.out.print(uniqueMappings.get(i)[j] + " ");
////				}
////				System.out.println("frequency: " + uniqueMappingsFrequencies.get(i));
////			}
////		}
		
		acceptRatio = ((double) acceptCount) / numberOfIterationsPerTemperature;

		return totalDeltaCost;
	}
	
	public void setNumberOfIterationsPerTemperature() {
		numberOfIterationsPerTemperature = (nodesNumber * (nodesNumber - 1)) / 2 - ((nodesNumber - coresNumber - 1) * (nodesNumber - coresNumber)) / 2;
//		numberOfIterationsPerTemperature = coresNumber * (nodesNumber - 1);
//		numberOfIterationsPerTemperature = nodesNumber - 2; // diameter of the 2D mesh 
	}
	
	public void setInitialTemperature() {
//		temperature = 100;
		temperature = 1;
	}
	
	public double getFinalTemperature() {
		return 1e-3;
	}
	
	public void decreaseTemperature() {
		// geometric temperature schedule (with ratio q = 0.9)
		temperature = 0.9 * temperature;
	}
	
	public boolean terminate() {
		if (logger.isDebugEnabled()) {
			logger.debug("current temperature < final temperature (" + getFinalTemperature() + ") "
					+ (MathUtils.definitelyLessThan((float) temperature, (float) getFinalTemperature())));
			logger.debug("numberOfConsecutiveRejectedMoves >= numberOfIterationsPerTemperature "
					+ (numberOfConsecutiveRejectedMoves >= numberOfIterationsPerTemperature)
					+ " ("
					+ numberOfConsecutiveRejectedMoves
					+ " < "
					+ numberOfIterationsPerTemperature + ")");
		}
		return MathUtils.definitelyLessThan((float)temperature, (float)getFinalTemperature()) 
			&& numberOfConsecutiveRejectedMoves >= numberOfIterationsPerTemperature;
	}

	private void anneal() {
		double totalDeltaCost;

		if (!buildRoutingTable) {
			linkBandwidthUsage = new int[linksNumber];
		} else {
			synLinkBandwithUsage = new int[edgeSize][edgeSize][4];
			saRoutingTable = new int[edgeSize][edgeSize][nodesNumber][nodesNumber];
			for (int i = 0; i < saRoutingTable.length; i++) {
				for (int j = 0; j < saRoutingTable[i].length; j++) {
					for (int k = 0; k < saRoutingTable[i][j].length; k++) {
						for (int l = 0; l < saRoutingTable[i][j][k].length; l++) {
							saRoutingTable[i][j][k][l] = -2;
						}
					}
				}
			}
		}

		// set up the global control parameters for this annealing run
		mappingIteration = 0;
		initialCost = calculateTotalCost();
		currentCost = initialCost;

		setNumberOfIterationsPerTemperature();

		initRand(1234567);

		setInitialTemperature();

		/* here is the temperature cooling loop of the annealer */
		while(!terminate()) {
			System.out.println("Round " + mappingIteration + ":");
			System.out.println("Current Annealing temperature " + temperature);

			totalDeltaCost = annealAtTemperature();
			
//			System.exit(-1);

			System.out.println("total delta cost " + totalDeltaCost);
			System.out.println("Current cost " + currentCost);
			System.out.println("Accept ratio " + acceptRatio);
			
//			printCurrentMapping();

			// save the relevant info to test for frozen after the NEXT
			// temperature.
			mappingIteration++;
			decreaseTemperature();
		}
		// return the best mapping found during the entire annealing process!!! (not the last mapping found)
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore("-1");
		}
		for (int i = 0; i < cores.length; i++) {
			cores[i].setNodeId(-1);
		}
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore(bestSolution[i]);
			if (!"-1".equals(bestSolution[i])) {
				cores[Integer.valueOf(bestSolution[i])].setNodeId(i);
			}
		}
		if (buildRoutingTable) {
			programRouters();
		}
	}

	/**
	 * Changes the current mapping by moving a core from one node to another.
	 * This implies that two nodes are changed.
	 * 
	 * @return the IDs of the two changed nodes
	 */
	private int[] move() {
//		return makeRandomSwap();
		return makeTopologicalMove();
	}
	
	/**
	 * Randomly picks two nodes and swaps them
	 * 
	 * @return an array with exactly 2 integers
	 */
	private int[] makeRandomSwap() {
		int node1 = (int) uniformIntegerRandomVariable(0, nodesNumber - 1);
		int node2 = -1;

		int cnt = 0;
		while (true) {
			cnt++;
			// select two nodes to swap
			node2 = (int) uniformIntegerRandomVariable(0, nodesNumber - 1);
			if (node1 != node2
					&& (!"-1".equals(nodes[node1].getCore()) || !"-1"
							.equals(nodes[node2].getCore()))) {
				break;
			}
		}
		if (cnt > 1 && logger.isTraceEnabled()) {
			logger.trace("The nodes to swap were randomly found after " + cnt + " trials");
		}

		// Swap the processes attached to these two nodes
		swapProcesses(node1, node2);
		return new int[] { node1, node2 };
	}
	
	/**
	 * Randomly selects a node (n1) that has a core (c1) mapped to it. Core c1
	 * is allowed to be placed only onto the NoC nodes that have enough
	 * neighbors so that all c1's communications have a corresponding node
	 * neighbor. If c1 has more communications than the maximum neighbors of a
	 * node, the node with a maximum neighborhood are used. This node is swapped
	 * randomly with another node (n2), unless c1 receives data from just a core
	 * (c2). In this case, c1 will be placed in one of the neighbors of n2 that
	 * doesn't have a core mapped to it. If there isn't an empty neighbor, we
	 * search for a neighbor with a core which doesn't communicate with c2. If
	 * we still don't find a neighbor, we fall back to random swapping. Note
	 * that is multiple neighbors are found, the chosen one is determined
	 * randomly.
	 * 
	 * @return the two swapped nodes
	 */
	private int[] makeTopologicalMove() {
		int node1;
		int node2;
		
		do {
			node1 = (int) uniformIntegerRandomVariable(0, nodesNumber - 1);
		} while ("-1".equals(nodes[node1].getCore()));
		
		int core1 = Integer.valueOf(nodes[node1].getCore());
		
		if (logger.isDebugEnabled()) {
			logger.debug("Selected node " + node1 + " for moving. It has core " + core1);
			logger.debug("Node " + node1 + " communicates with nodes " + nodeNeighbors[node1]);
			logger.debug("Core " + core1 + " communicates with cores " + coreNeighbors[core1]);
		}
		
		// check where core1 could be placed so that its number of
		// communications with other cores can be satisfied by the number of
		// node neighbors (one node neighbor for each communication)
		//
		// obviously, node node1 will not be among core1's allowed nodes
		List<Integer> core1AllowedNodes = new ArrayList<Integer>();
		for (int i = 0; i < nodeNeighbors.length; i++) {
			if (i != node1
					&& (nodeNeighbors[i].size() >= coreNeighbors[core1].size()
							|| nodeNeighbors[i] .size() == maxNodeNeighbors)) {
				core1AllowedNodes.add(i);
				if (logger.isDebugEnabled()) {
					logger.debug("Core " + core1 + " is allowed to be placed on node " + i);
				}
			}
		}
		
		// check if core1 receives data only from one core
		long[] fromCommunication = cores[core1].getFromCommunication();
		int fromCoresCount = 0;
		int core2 = -1; // core2 has meaning only in the (fromCoresCount == 1) if
		for (int i = 0; i < fromCommunication.length; i++) {
			if (fromCommunication[i] > 0) {
				fromCoresCount++;
				core2 = i;
				if (fromCoresCount > 1) {
					// no need to search further on
					break;
				}
			}
		}
		if (fromCoresCount == 1) {
			if (logger.isDebugEnabled()) {
				logger.debug("Core " + core1 + " receives data only from core "
						+ core2 + ". Trying to compactly place core " + core1
						+ " (onto a neighbor node of core " + core2 + ")");
			}
			int core2Node = cores[core2].getNodeId(); // the node that has core2
			logger.assertLog(core2Node >= 0, "Couldn't find the node to which core " + core2 + " is placed!");
			if (logger.isDebugEnabled()) {
				logger.debug("Core " + core2 + " is placed onto node " + core2Node);
			}
			// determine the neighboring nodes of node core2Node that are among the allowed nodes for core1
			Set<Integer> core2NodeNeighborsAllowedForCore1 = new LinkedHashSet<Integer>();
			for (Integer neighbor : nodeNeighbors[core2Node]) {
				if (core1AllowedNodes.contains(neighbor)) {
					core2NodeNeighborsAllowedForCore1.add(neighbor);
					if (logger.isDebugEnabled()) {
						logger.debug("Node " + neighbor + " is a neighbor of node " + core2Node + 
								" and also one of the nodes allowed for core " + core1);
					}
				}
			}
			// determine the neighboring nodes of node core2Node which are unoccupied
			List<Integer> unoccupiedNodes = null;
			if (cores.length < nodes.length) {
				unoccupiedNodes = new ArrayList<Integer>(core2NodeNeighborsAllowedForCore1.size());
				for (Iterator<Integer> iterator = core2NodeNeighborsAllowedForCore1.iterator(); iterator.hasNext();) {
					Integer neighbor = iterator.next();
					if ("-1".equals(nodes[neighbor].getCore())) {
						if (logger.isDebugEnabled()) {
							logger.debug("Node " + neighbor + " is unoccupied");
						}
						unoccupiedNodes.add(neighbor);
					}
				}
			}
			if (unoccupiedNodes != null && unoccupiedNodes.size() > 0) {
				int r = (int) uniformIntegerRandomVariable(0, unoccupiedNodes.size() - 1);
				node2 = unoccupiedNodes.get(r);
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("All neighbors of node " + core2Node + 
							" are occupied or are not among the allowed nodes for core " + core1 + ". " +
							"Searching for neighboring nodes of node " + core2Node + 
							" which have cores that do not communicate with core " + core1);
				}
				// determine the neighboring nodes of node core2Node which have cores that do not communicate with core2
				List<Integer> notCommunicatingNodes = new ArrayList<Integer>(core2NodeNeighborsAllowedForCore1.size());
				for (Iterator<Integer> iterator = core2NodeNeighborsAllowedForCore1.iterator(); iterator.hasNext();) {
					Integer neighbor = iterator.next();
					Core core = cores[Integer.valueOf(nodes[neighbor].getCore())];
					if (core.getToCommunication()[core2] == 0 && core.getFromCommunication()[core2] == 0) {
						if (logger.isDebugEnabled()) {
							logger.debug("Node " + neighbor + " has core " + core.getCoreId() + 
									" (APCG " + core.getApcgId() + ") that does not communicate with core " + core2);
						}
						notCommunicatingNodes.add(neighbor);
					}
					
				}
				if (notCommunicatingNodes.size() > 0) {
					int r = (int) uniformIntegerRandomVariable(0, notCommunicatingNodes.size() - 1);
					node2 = notCommunicatingNodes.get(r);
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("No suitable neighbor node found. Falling back to random swap " +
								"(restricted to allowed nodes for core " + core1 + " )...");
					}
					// core1 will be placed onto one of the allowed nodes
					int i = (int) uniformIntegerRandomVariable(0, core1AllowedNodes.size() - 1);
					node2 = core1AllowedNodes.get(i);
				}
			}
		} else {
			// core1 will be placed onto one of the allowed nodes
			int i = (int) uniformIntegerRandomVariable(0, core1AllowedNodes.size() - 1);
			node2 = core1AllowedNodes.get(i);
		}
		logger.assertLog(
				node1 != -1 && node2 != -1 && node1 != node2,
				"At least one node is not defined (i.e. = -1) or the two nodes are identical; node1 = "
						+ node1 + ", node2 = " + node2);
		if (logger.isDebugEnabled()) {
			logger.debug("Swapping nodes " + node1 + " and " + node2);
		}
		swapProcesses(node1, node2);
		
		return new int[] { node1, node2 };
	}

	/**
	 * Swaps the processes from nodes with IDs t1 and t2
	 * 
	 * @param t1
	 *            the ID of the first node
	 * @param t2
	 *            the ID of the second node
	 */
	private void swapProcesses(int t1, int t2) {
		NodeType node1 = nodes[t1];
		NodeType node2 = nodes[t2];
		logger.assertLog(node1 != null, null);
		logger.assertLog(node2 != null, null);

		int p1 = Integer.valueOf(node1.getCore());
		int p2 = Integer.valueOf(node2.getCore());
		
		logger.assertLog(t1 == Integer.valueOf(node1.getId()), null);
		logger.assertLog(t2 == Integer.valueOf(node2.getId()), null);
		logger.assertLog(p1 == Integer.valueOf(node1.getCore()), null);
		logger.assertLog(p2 == Integer.valueOf(node2.getCore()), null);
		
		if (logger.isTraceEnabled()) {
			logger.trace("Swapping process " + p1 + " of node " + t1
					+ " with process " + p2 + " of node " + t2);
		}
		
		node1.setCore(Integer.toString(p2));
		node2.setCore(Integer.toString(p1));
		if (p1 != -1) {
			Core process = cores[p1];
			if (process == null) {
				process = new Core(p1, null, t2);
			} else {
				process.setNodeId(t2);
			}
		}
		if (p2 != -1) {
			Core process = cores[p2];
			if (process == null) {
				process = new Core(p2, null, t1);
			} else {
				process.setNodeId(t1);
			}
		}
	}

	/**
	 * Calculate the total cost in terms of the sum of the energy consumption
	 * and the penalty of the link overloading
	 * 
	 * @return the total cost
	 */
	private float calculateTotalCost() {
		// the communication energy part
		float energyCost = calculateCommunicationEnergy();
		float overloadCost;
		// now calculate the overloaded BW cost
		if (!buildRoutingTable) {
			overloadCost = calculateOverloadWithFixedRouting();
		} else {
			overloadCost = calculateOverloadWithAdaptiveRouting();
		}
		if (logger.isTraceEnabled()) {
			logger.trace("energy cost " + energyCost);
			logger.trace("overload cost " + overloadCost);
			logger.trace("total cost " + (energyCost + overloadCost));
		}
		return energyCost + overloadCost;
	}

	/**
	 * Computes the overload of the links when no routing is performed
	 * 
	 * @return the overload
	 */
	private float calculateOverloadWithFixedRouting() {
		Arrays.fill(linkBandwidthUsage, 0);
		for (int proc1 = 0; proc1 < coresNumber; proc1++) {
			for (int proc2 = proc1 + 1; proc2 < coresNumber; proc2++) {
				if (cores[proc1].getToBandwidthRequirement()[proc2] > 0) {
					int node1 = cores[proc1].getNodeId();
					int node2 = cores[proc2].getNodeId();
					for (int i = 0; i < linkUsageList[node1][node2].size(); i++) {
						int linkId = linkUsageList[node1][node2].get(i);
						linkBandwidthUsage[linkId] += cores[proc1]
								.getToBandwidthRequirement()[proc2];
					}
				}
				if (cores[proc1].getFromBandwidthRequirement()[proc2] > 0) {
					int node1 = cores[proc1].getNodeId();
					int node2 = cores[proc2].getNodeId();
					for (int i = 0; i < linkUsageList[node1][node2].size(); i++) {
						int linkId = linkUsageList[node2][node1].get(i);
						linkBandwidthUsage[linkId] += cores[proc1]
								.getFromBandwidthRequirement()[proc2];
					}
				}
			}
		}
		float overloadCost = 0;
		for (int i = 0; i < linksNumber; i++) {
			if (linkBandwidthUsage[i] > links[i].getBandwidth()) {
				overloadCost = ((float) linkBandwidthUsage[i])
						/ links[i].getBandwidth().floatValue() - 1.0f;
			}
		}
		overloadCost *= OVERLOAD_UNIT_COST;
		return overloadCost;
	}

	/**
	 * Computes the overload of the links when routing is performed
	 * 
	 * @return the overload
	 */
	private float calculateOverloadWithAdaptiveRouting() {
		float overloadCost = 0.0f;

		// Clear the link usage
		for (int i = 0; i < edgeSize; i++) {
			for (int j = 0; j < edgeSize; j++) {
				Arrays.fill(synLinkBandwithUsage[i][j], 0);
			}
		}

		for (int src = 0; src < coresNumber; src++) {
			for (int dst = 0; dst < coresNumber; dst++) {
				int node1 = cores[src].getNodeId();
				int node2 = cores[dst].getNodeId();
				if (cores[src].getToBandwidthRequirement()[dst] > 0) {
					routeTraffic(node1, node2,
							cores[src].getToBandwidthRequirement()[dst]);
				}
			}
		}

		for (int i = 0; i < edgeSize; i++) {
			for (int j = 0; j < edgeSize; j++) {
				for (int k = 0; k < 4; k++) {
					if (synLinkBandwithUsage[i][j][k] > links[0].getBandwidth()) {
						overloadCost += ((float) synLinkBandwithUsage[i][j][k])
								/ links[0].getBandwidth() - 1.0;
					}
				}
			}
		}

		overloadCost *= OVERLOAD_UNIT_COST;
		return overloadCost;
	}

	/**
	 * Routes the traffic. Hence, the routing table is computed here by the
	 * algorithm.
	 * 
	 * @param srcNode
	 *            the source node
	 * @param dstNode
	 *            the destination node
	 * @param bandwidth
	 *            the bandwidth
	 */
	private void routeTraffic(int srcNode, int dstNode, long bandwidth) {
		boolean commit = true;

		int srcRow = Integer.valueOf(getNodeTopologyParameter(nodes[srcNode], TopologyParameter.ROW));
		int srcColumn = Integer.valueOf(getNodeTopologyParameter(nodes[srcNode], TopologyParameter.COLUMN));
		int dstRow = Integer.valueOf(getNodeTopologyParameter(nodes[dstNode], TopologyParameter.ROW));
		int dstColumn = Integer.valueOf(getNodeTopologyParameter(nodes[dstNode], TopologyParameter.COLUMN));

		int row = srcRow;
		int col = srcColumn;

		int direction = -2;
		while (row != dstRow || col != dstColumn) {
			// For west-first routing
			if (LegalTurnSet.WEST_FIRST.equals(legalTurnSet)) {
				if (col > dstColumn) {
					// step west
					direction = WEST;
				} else {
					if (col == dstColumn) {
						direction = (row < dstRow) ? NORTH : SOUTH;
					} else {
						if (row == dstRow) {
							direction = EAST;
						}
						else {
							// Here comes the flexibility. We can choose whether to
							// go
							// vertical or horizontal
							int direction1 = (row < dstRow) ? NORTH : SOUTH;
							if (synLinkBandwithUsage[row][col][direction1] < synLinkBandwithUsage[row][col][EAST]) {
								direction = direction1;
							} else {
								if (synLinkBandwithUsage[row][col][direction1] > synLinkBandwithUsage[row][col][EAST]) {
									direction = EAST;
								} else {
									// In this case, we select the direction
									// which has the
									// longest
									// distance to the destination
									if ((dstColumn - col) * (dstColumn - col) <= (dstRow - row)
											* (dstRow - row)) {
										direction = direction1;
									} else {
										// Horizontal move
										direction = EAST;
									}
								}
							}
						}
					}
				}
			}
			// For odd-even routing
			else {
				if (LegalTurnSet.ODD_EVEN.equals(legalTurnSet)) {
					int e0 = dstColumn - col;
					int e1 = dstRow - row;
					if (e0 == 0) {
						// currently the same column as destination
						direction = (e1 > 0) ? NORTH : SOUTH;
					} else {
						if (e0 > 0) { // eastbound messages
							if (e1 == 0) {
								direction = EAST;
							} else {
								int direction1 = -1, direction2 = -1;
								if (col % 2 == 1 || col == srcColumn) {
									direction1 = (e1 > 0) ? NORTH : SOUTH;
								}
								if (dstColumn % 2 == 1 || e0 != 1) {
									direction2 = EAST;
								}
								if (direction1 == -1 && direction2 == -1) {
									logger.fatal("Error. Exiting...");
									System.exit(0);
								}
								if (direction1 == -1) {
									direction = direction2;
								} else {
									if (direction2 == -1) {
										direction = direction1;
									} else {
										// we have two choices
										direction = (synLinkBandwithUsage[row][col][direction1] < synLinkBandwithUsage[row][col][direction2]) ? direction1
												: direction2;
									}
								}
							}
						} else { // westbound messages
							if (col % 2 != 0 || e1 == 0) {
								direction = WEST;
							} else {
								int direction1 = (e1 > 0) ? NORTH : SOUTH;
								direction = (synLinkBandwithUsage[row][col][WEST] < synLinkBandwithUsage[row][col][direction1]) ? WEST
										: direction1;
							}
						}
					}
				}
			}
			synLinkBandwithUsage[row][col][direction] += bandwidth;

			if (commit) {
				saRoutingTable[row][col][srcNode][dstNode] = direction;
			}
			switch (direction) {
			case SOUTH:
				row--;
				break;
			case NORTH:
				row++;
				break;
			case EAST:
				col++;
				break;
			case WEST:
				col--;
				break;
			default:
				logger.error("Error. Unknown direction!");
				break;
			}
		}
	}

	private void programRouters() {
		// clean all the old routing table
		for (int nodeId = 0; nodeId < nodesNumber; nodeId++) {
			for (int srcNode = 0; srcNode < nodesNumber; srcNode++) {
				for (int dstNode = 0; dstNode < nodesNumber; dstNode++) {
					if (nodeId == dstNode) {
						routingTables[Integer.valueOf(nodes[nodeId].getId())][srcNode][dstNode] = -1;
					} else {
						routingTables[Integer.valueOf(nodes[nodeId].getId())][srcNode][dstNode] = -2;
					}
				}
			}
		}

		for (int row = 0; row < edgeSize; row++) {
			for (int col = 0; col < edgeSize; col++) {
				int nodeId = row * edgeSize + col;
				for (int srcNode = 0; srcNode < nodesNumber; srcNode++) {
					for (int dstNode = 0; dstNode < nodesNumber; dstNode++) {
						int linkId = locateLink(row, col,
								saRoutingTable[row][col][srcNode][dstNode]);
						if (linkId != -1) {
							routingTables[Integer.valueOf(nodes[nodeId].getId())][srcNode][dstNode] = linkId;
						}
					}
				}
			}
		}
	}

	/**
	 * Computes the communication energy
	 * 
	 * @return the communication energy
	 */
	private float calculateCommunicationEnergy() {
		float switchEnergy = calculateSwitchEnergy();
		float linkEnergy = calculateLinkEnergy();
		float bufferEnergy = calculateBufferEnergy();
		if (logger.isTraceEnabled()) {
			logger.trace("switch energy " + switchEnergy);
			logger.trace("link energy " + linkEnergy);
			logger.trace("buffer energy " + bufferEnergy);
		}
		return switchEnergy + linkEnergy + bufferEnergy;
	}

	private float calculateSwitchEnergy() {
		float energy = 0;
		for (int src = 0; src < nodesNumber; src++) {
			for (int dst = 0; dst < nodesNumber; dst++) {
				int srcProc = Integer.valueOf(nodes[src].getCore());
				int dstProc = Integer.valueOf(nodes[dst].getCore());
				if (srcProc > -1 && dstProc > -1) {
					long commVol = cores[srcProc].getToCommunication()[dstProc];
					if (commVol > 0) {
						energy += nodes[src].getCost() * commVol;
						NodeType currentNode = nodes[src];
						if (logger.isTraceEnabled()) {
							logger.trace("adding " + currentNode.getCost() + " * "
									+ commVol + " (core " + srcProc + " to core "
									+ dstProc + ") current node "
									+ currentNode.getId());
						}
						while (Integer.valueOf(currentNode.getId()) != dst) {
							int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
							LinkType link = links[linkId];
							String node = "-1";
							// we work with with bidirectional links
							if (currentNode.getId().equals(link.getFirstNode())) {
								node = link.getSecondNode();
							} else {
								if (currentNode.getId().equals(link.getSecondNode())) {
									node = link.getFirstNode();
								}
							}
							currentNode = nodes[Integer.valueOf(node)];
							energy += currentNode.getCost() * commVol;
							if (logger.isTraceEnabled()) {
								logger.trace("adding " + currentNode.getCost()
										+ " * " + commVol + " (core " + srcProc
										+ " to core " + dstProc + ") current node "
										+ currentNode.getId() + " link ID "
										+ linkId);
							}
						}
					}
				}
			}
		}
		return energy;
	}

	private float calculateLinkEnergy() {
		float energy = 0;
		for (int src = 0; src < nodesNumber; src++) {
			for (int dst = 0; dst < nodesNumber; dst++) {
				int srcProc = Integer.valueOf(nodes[src].getCore());
				int dstProc = Integer.valueOf(nodes[dst].getCore());
				if (srcProc > -1 && dstProc > -1) {
					long commVol = cores[srcProc].getToCommunication()[dstProc];
					if (commVol > 0) {
						NodeType currentNode = nodes[src];
						while (Integer.valueOf(currentNode.getId()) != dst) {
							int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
							energy += links[linkId].getCost() * commVol;
							LinkType link = links[linkId];
							String node = "-1";
							// we work with with bidirectional links
							if (currentNode.getId().equals(link.getFirstNode())) {
								node = link.getSecondNode();
							} else {
								if (currentNode.getId().equals(link.getSecondNode())) {
									node = link.getFirstNode();
								}
							}
							currentNode = nodes[Integer.valueOf(node)];
						}
					}
				}
			}
		}
		return energy;
	}

	private float calculateBufferEnergy() {
		float energy = 0;
		for (int src = 0; src < nodesNumber; src++) {
			for (int dst = 0; dst < nodesNumber; dst++) {
				int srcProc = Integer.valueOf(nodes[src].getCore());
				int dstProc = Integer.valueOf(nodes[dst].getCore());
				if (srcProc > -1 && dstProc > -1) {
					long commVol = cores[srcProc].getToCommunication()[dstProc];
					if (commVol > 0) {
						NodeType currentNode = nodes[src];
						while (Integer.valueOf(currentNode.getId()) != dst) {
							int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
							energy += (bufReadEBit + bufWriteEBit) * commVol;
							LinkType link = links[linkId];
							String node = "-1";
							// we work with with bidirectional links
							if (currentNode.getId().equals(link.getFirstNode())) {
								node = link.getSecondNode();
							} else {
								if (currentNode.getId().equals(link.getSecondNode())) {
									node = link.getFirstNode();
								}
							}
							currentNode = nodes[Integer.valueOf(node)];
						}
						energy += bufWriteEBit * commVol;
					}
				}
			}
		}
		return energy;
	}

	/**
	 * find out the link ID. If the direction is not set, return -1
	 * 
	 * @param row
	 *            the row from the 2D mesh
	 * @param column
	 *            the column form the 2D mesh
	 * @param direction
	 *            the direction
	 * @return the link ID
	 */
	private int locateLink(int row, int column, int direction) {
		int origRow = row;
		int origColumn = column;
		switch (direction) {
		case NORTH:
			row++;
			break;
		case SOUTH:
			row--;
			break;
		case EAST:
			column++;
			break;
		case WEST:
			column--;
			break;
		default:
			return -1;
		}
		int linkId;
		for (linkId = 0; linkId < linksNumber; linkId++) {
			if (Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getFirstNode())], TopologyParameter.ROW)) == origRow
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getFirstNode())], TopologyParameter.COLUMN)) == origColumn
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getSecondNode())], TopologyParameter.ROW)) == row
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getSecondNode())], TopologyParameter.COLUMN)) == column)
				break;
			if (Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getSecondNode())], TopologyParameter.ROW)) == origRow
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getSecondNode())], TopologyParameter.COLUMN)) == origColumn
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getFirstNode())], TopologyParameter.ROW)) == row
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getFirstNode())], TopologyParameter.COLUMN)) == column)
				break;
		}
		if (linkId == linksNumber) {
			logger.fatal("Error in locating link");
			System.exit(-1);
		}
		return linkId;
	}

	private boolean verifyBandwidthRequirement() {
		generateLinkUsageList();

		int[] usedBandwidth = new int[linksNumber];
		
		for (int i = 0; i < linksNumber; i++) {
			usedBandwidth[i] = 0;
		}

		for (int src = 0; src < nodesNumber; src++) {
			for (int dst = 0; dst < nodesNumber; dst++) {
	            if (src == dst) {
	                continue;
	            }
	            int srcProc = Integer.valueOf(nodes[src].getCore());
	            int dstProc = Integer.valueOf(nodes[dst].getCore());
	            if (srcProc > -1 && dstProc > -1) {
	            	long commLoad = cores[srcProc].getToBandwidthRequirement()[dstProc];
		            if (commLoad == 0) {
		                continue;
		            }
		            NodeType currentNode = nodes[src];
		            while (Integer.valueOf(currentNode.getId()) != dst) {
		                int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
		                LinkType link = links[linkId];
						String node = "-1";
						// we work with with bidirectional links
						if (currentNode.getId().equals(link.getFirstNode())) {
							node = link.getSecondNode();
						} else {
							if (currentNode.getId().equals(link.getSecondNode())) {
								node = link.getFirstNode();
							}
						}
						currentNode = nodes[Integer.valueOf(node)];
		                usedBandwidth[linkId] += commLoad;
		            }
	            }
	        }
	    }
	    //check for the overloaded links
	    int violations = 0;
		for (int i = 0; i < linksNumber; i++) {
	        if (usedBandwidth[i] > links[i].getBandwidth()) {
	        	System.out.println("Link " + i + " is overloaded: " + usedBandwidth[i] + " > "
	                 + links[i].getBandwidth());
	            violations ++;
	        }
	    }
		return violations == 0;
	}
	
	/**
	 * Performs an analysis of the mapping. It verifies if bandwidth
	 * requirements are met and computes the link, switch and buffer energy.
	 * The communication energy is also computed (as a sum of the three energy
	 * components).
	 */
	public void analyzeIt() {
	    logger.info("Verify the communication load of each link...");
	    String bandwidthRequirements;
	    if (verifyBandwidthRequirement()) {
	    	logger.info("Succes");
	    	bandwidthRequirements = "Succes";
	    }
	    else {
	    	logger.info("Fail");
	    	bandwidthRequirements = "Fail";
	    }
	    if (logger.isDebugEnabled()) {
		    logger.debug("Energy consumption estimation ");
		    logger.debug("(note that this is not exact numbers, but serve as a relative energy indication) ");
		    logger.debug("Energy consumed in link is " + calculateLinkEnergy());
		    logger.debug("Energy consumed in switch is " + calculateSwitchEnergy());
		    logger.debug("Energy consumed in buffer is " + calculateBufferEnergy());
	    }
	    float energy = calculateCommunicationEnergy();
	    logger.info("Total communication energy consumption is " + energy);
	    
		MapperDatabase.getInstance().setOutputs(
				new String[] { "bandwidthRequirements", "energy" },
				new String[] { bandwidthRequirements, Float.toString(energy) });
	}
	
	private void saveRoutingTables() {
		if (logger.isInfoEnabled()) {
			logger.info("Saving the routing tables");
		}
		
		for (int i = 0; i < nodes.length; i++) {
			int[][] routingEntries = routingTables[Integer.valueOf(nodes[i].getId())];
			for (int j = 0; j < routingEntries.length; j++) {
				for (int k = 0; k < routingEntries[j].length; k++) {
					if (routingEntries[j][k] >= 0) {
						RoutingTableEntryType routingTableEntry = new RoutingTableEntryType();
						routingTableEntry.setSource(Integer.toString(j));
						routingTableEntry.setDestination(Integer.toString(k));
						routingTableEntry.setLink(Integer.toString(routingEntries[j][k]));
						nodes[i].getRoutingTableEntry().add(routingTableEntry);
					}
				}
			}
		}
	}
	
	private void saveTopology() {
		try {
			// save the nodes
			JAXBContext jaxbContext = JAXBContext.newInstance(NodeType.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
			ObjectFactory nodeFactory = new ObjectFactory();
			for (int i = 0; i < nodes.length; i++) {
				StringWriter stringWriter = new StringWriter();
				JAXBElement<NodeType> node = nodeFactory.createNode(nodes[i]);
				marshaller.marshal(node, stringWriter);	
				String routing = "";
				if (buildRoutingTable) {
					routing = "_routing";
				}
				File file = new File(topologyDir + File.separator + "sa" + routing
						+ File.separator + "nodes");
				file.mkdirs();
				PrintWriter pw = new PrintWriter(file + File.separator
						+ "node-" + i + ".xml");
				logger.debug("Saving the XML for node " + i);
				pw.write(stringWriter.toString());
				pw.close();
			}
			// save the links
			jaxbContext = JAXBContext.newInstance(LinkType.class);
			marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
			ro.ulbsibiu.acaps.noc.xml.link.ObjectFactory linkFactory = new ro.ulbsibiu.acaps.noc.xml.link.ObjectFactory();
			for (int i = 0; i < links.length; i++) {
				StringWriter stringWriter = new StringWriter();
				JAXBElement<LinkType> link = linkFactory.createLink(links[i]);
				marshaller.marshal(link, stringWriter);
				String routing = "";
				if (buildRoutingTable) {
					routing = "_routing";
				}
				File file = new File(topologyDir + File.separator + "sa" + routing
						+ File.separator + "links");
				file.mkdirs();
				PrintWriter pw = new PrintWriter(file + File.separator
						+ "link-" + i + ".xml");
				logger.debug("Saving the XML for link " + i);
				pw.write(stringWriter.toString());
				pw.close();
			}
		} catch (JAXBException e) {
			logger.error("JAXB encountered an error", e);
		} catch (FileNotFoundException e) {
			logger.error("File not found", e);
		}
	}
	
	private void computeCoreNeighbors() {
		coreNeighbors = new LinkedHashSet[cores.length];
		for (int i = 0; i < cores.length; i++) {
			coreNeighbors[i] = new LinkedHashSet<Integer>();
			long[] fromCommunication = cores[i].getFromCommunication();
			for (int j = 0; j < fromCommunication.length; j++) {
				if (MathUtils.definitelyGreaterThan(fromCommunication[j], 0)) {
					coreNeighbors[i].add(j);
				}
			}
			long[] toCommunication = cores[i].getToCommunication();
			for (int j = 0; j < toCommunication.length; j++) {
				if (MathUtils.definitelyGreaterThan(toCommunication[j], 0)) {
					coreNeighbors[i].add(j);
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Core " + cores[i].getCoreId() + "(APCG "
						+ cores[i].getApcgId() + ") communicates with "
						+ coreNeighbors[i].size() + " cores");
			}
		}
	}
	
	@Override
	public String map() throws TooFewNocNodesException {
		if (nodesNumber < coresNumber) {
			throw new TooFewNocNodesException(coresNumber, nodesNumber);
		}

		computeCoreNeighbors();
		mapCoresToNocNodesRandomly();
		printCurrentMapping();

		if (coresNumber == 1) {
			logger.info("Simulated Annealing Test will not start for mapping a single core. This core simply mapped randomly.");
		} else {
			logger.info("Start mapping...");

			logger.assertLog((coresNumber == ((int) cores.length)), null);

		}
		Date startDate = new Date();
		HeapUsageMonitor monitor = new HeapUsageMonitor();
		monitor.startMonitor();
		long userStart = TimeUtils.getUserTime();
		long sysStart = TimeUtils.getSystemTime();
		long realStart = System.nanoTime();
		
		if (coresNumber > 1) {
			anneal();
		}
		
		long userEnd = TimeUtils.getUserTime();
		long sysEnd = TimeUtils.getSystemTime();
		long realEnd = System.nanoTime();
		monitor.stopMonitor();
		logger.info("Mapping process finished successfully.");
		logger.info("Time: " + (realEnd - realStart) / 1e9 + " seconds");
		logger.info("Memory: " + monitor.getAverageUsedHeap()
				/ (1024 * 1024 * 1.0) + " MB");
		
		logger.info("Best mapping found at round " + bestSolutionIteration + 
				", temperature " + bestSolutionTemperature + ", with cost " + bestCost);
		
		saveRoutingTables();
		
		saveTopology();

		MappingType mapping = new MappingType();
		mapping.setId(MAPPER_ID);
		mapping.setRuntime(new Double(realEnd - realStart));
		for (int i = 0; i < nodes.length; i++) {
			if (!"-1".equals(nodes[i].getCore())) {
				MapType map = new MapType();
				map.setNode(nodes[i].getId());
				map.setCore(Integer.toString(cores[Integer.parseInt(nodes[i].getCore())].getCoreId()));
				map.setApcg(cores[Integer.parseInt(nodes[i].getCore())].getApcgId());
				mapping.getMap().add(map);
			}
		}
		StringWriter stringWriter = new StringWriter();
		ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory mappingFactory = new ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory();
		JAXBElement<MappingType> jaxbElement = mappingFactory.createMapping(mapping);
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(MappingType.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
			marshaller.marshal(jaxbElement, stringWriter);
		} catch (JAXBException e) {
			logger.error("JAXB encountered an error", e);
		}
		
		int benchmarkId = MapperDatabase.getInstance().getBenchmarkId(benchmarkName, ctgId);
		int nocTopologyId = MapperDatabase.getInstance().getNocTopologyId(topologyName, topologySize);
		MapperDatabase.getInstance().saveMapping(getMapperId(),
				"Simulated Annealing (test)", benchmarkId, apcgId, nocTopologyId,
				stringWriter.toString(), startDate,
				(realEnd - realStart) / 1e9, (userEnd - userStart) / 1e9,
				(sysEnd - sysStart) / 1e9, monitor.getAverageUsedHeap(), null);

		return stringWriter.toString();
	}

	private void parseTrafficConfig(String filePath, double linkBandwidth)
			throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(filePath)));

		int id = -1;

		String line;
		while ((line = br.readLine()) != null) {
			// line starting with "#" are comments
			if (line.startsWith("#")) {
				continue;
			}

			if (line.contains("@NODE")) {
				try {
					id = Integer.valueOf(line.substring("@NODE".length() + 1));
				} catch (NumberFormatException e) {
					logger.error("The node from line '" + line + "' is not a number", e);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("ID = " + id);
				}
			}

			if (line.contains("packet_to_destination_rate")) {
				String substring = line.substring(
						"packet_to_destination_rate".length() + 1).trim();
				int dstId = -1;
				try {
					dstId = Integer.valueOf(substring.substring(0,
							substring.indexOf("\t")));
					if (logger.isTraceEnabled()) {
						logger.trace(" dst ID = " + dstId);
					}
				} catch (NumberFormatException e) {
					logger.error("The destination from line '" + line + "' is not a number", e);
				}
				double rate = 0;
				try {
					rate = Double.valueOf(substring.substring(substring
							.indexOf("\t") + 1));
					if (logger.isTraceEnabled()) {
						logger.trace(" rate = " + rate);
					}
				} catch (NumberFormatException e) {
					logger.error("The rate from line '" + line + "' is not a number", e);
				}

				if (rate > 1) {
					logger.fatal("Invalid rate!");
					System.exit(0);
				}
				cores[id].getToCommunication()[dstId] = (int) (rate * 1000000);
				cores[id].getToBandwidthRequirement()[dstId] = (int) (rate * 3 * linkBandwidth);
				cores[dstId].getFromCommunication()[id] = (int) (rate * 1000000);
				cores[dstId].getFromBandwidthRequirement()[id] = (int) (rate * 3 * linkBandwidth);
			}
		}

		br.close();
	}

	public static void main(String[] args) throws TooFewNocNodesException,
			IOException, JAXBException {
		final int applicationBandwithRequirement = 3; // a multiple of the communication volume
		final double linkBandwidth = 256E9;
		final float switchEBit = 0.284f;
		final float linkEBit = 0.449f;
		final float bufReadEBit = 1.056f;
		final float bufWriteEBit = 2.831f;
		
		MapperInputProcessor mapperInputProcessor = new MapperInputProcessor() {
			
			@Override
			public void useMapper(String benchmarkFilePath, String benchmarkName,
					String ctgId, String apcgId, List<CtgType> ctgTypes,
					List<ApcgType> apcgTypes, boolean doRouting) throws JAXBException,
					TooFewNocNodesException, FileNotFoundException {
				logger.info("Using a Simulated annealing mapper for "
						+ benchmarkFilePath + "ctg-" + ctgId + " (APCG " + apcgId + ")");
				
				SimulatedAnnealingTestMapper saMapper;
				int cores = 0;
				for (int k = 0; k < apcgTypes.size(); k++) {
					cores += apcgTypes.get(k).getCore().size();
				}
				int hSize = (int) Math.ceil(Math.sqrt(cores));
				hSize = Math.max(2, hSize); // using at least a 2x2 2D mesh
				String meshSize = hSize + "x" + hSize;
				logger.info("The algorithm has " + cores + " cores to map => working with a 2D mesh of size " + meshSize);
				// working with a 2D mesh topology
				String topologyName = "mesh2D";
				String topologyDir = ".." + File.separator + "NoC-XML"
						+ File.separator + "src" + File.separator
						+ "ro" + File.separator + "ulbsibiu"
						+ File.separator + "acaps" + File.separator
						+ "noc" + File.separator + "topology"
						+ File.separator + topologyName + File.separator
						+ meshSize;
				
				String[] parameters = new String[] {
						"applicationBandwithRequirement",
						"linkBandwidth",
						"switchEBit",
						"linkEBit",
						"bufReadEBit",
						"bufWriteEBit",
						"routing"};
				String values[] = new String[] {
						Integer.toString(applicationBandwithRequirement),
						Double.toString(linkBandwidth),
						Float.toString(switchEBit), Float.toString(linkEBit),
						Float.toString(bufReadEBit),
						Float.toString(bufWriteEBit),
						null};
				if (doRouting) {
					values[values.length - 1] = "true";
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// SA with routing
					saMapper = new SimulatedAnnealingTestMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							true, LegalTurnSet.ODD_EVEN, bufReadEBit,
							bufWriteEBit, switchEBit, linkEBit);
				} else {
					values[values.length - 1] = "false";
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// SA without routing
					saMapper = new SimulatedAnnealingTestMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							switchEBit, linkEBit);
				}
	
	//			// read the input data from a traffic.config file (NoCmap style)
	//			saMapper(
	//					"telecom-mocsyn-16tile-selectedpe.traffic.config",
	//					linkBandwidth);
				
				for (int k = 0; k < apcgTypes.size(); k++) {
					// read the input data using the Unified Framework's XML interface
					saMapper.parseApcg(apcgTypes.get(k), ctgTypes.get(k), applicationBandwithRequirement);
				}
				
	//			// This is just for checking that bbMapper.parseTrafficConfig(...)
	//			// and parseApcg(...) have the same effect
	//			bbMapper.printCores();
	
				String mappingXml = saMapper.map();
				File dir = new File(benchmarkFilePath + "ctg-" + ctgId);
				dir.mkdirs();
				String routing = "";
				if (doRouting) {
					routing = "_routing";
				}
				String mappingXmlFilePath = benchmarkFilePath + "ctg-" + ctgId
						+ File.separator + "mapping-" + apcgId + "_"
						+ saMapper.getMapperId() + routing + ".xml";
				PrintWriter pw = new PrintWriter(mappingXmlFilePath);
				logger.info("Saving the mapping XML file" + mappingXmlFilePath);
				pw.write(mappingXml);
				pw.close();
	
				logger.info("The generated mapping is:");
				saMapper.printCurrentMapping();
				
				saMapper.analyzeIt();
			}
		};
		mapperInputProcessor.processInput(args);
	}
}
