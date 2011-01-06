package ro.ulbsibiu.acaps.mapper.bb;

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
import java.util.Date;
import java.util.List;
import java.util.Random;

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
import ro.ulbsibiu.acaps.mapper.bb.MappingNode.RoutingEffort;
import ro.ulbsibiu.acaps.mapper.sa.Core;
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
 * Branch-and-Bound algorithm for Network-on-Chip (NoC) application mapping. The
 * implementation is based on the one from <a
 * href="http://www.ece.cmu.edu/~sld/wiki/doku.php?id=shared:nocmap">NoCMap</a>
 * 
 * <p>
 * Note that currently, this algorithm works only with N x N 2D mesh NoCs
 * </p>
 * 
 * @see MappingNode
 * 
 * @author cipi
 * 
 */
public class BranchAndBoundMapper implements Mapper {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(BranchAndBoundMapper.class);
	
	
	private static final String MAPPER_ID = "bb";

	static final int NORTH = 0;

	static final int SOUTH = 1;

	static final int EAST = 2;

	static final int WEST = 3;

	/** the number of tiles (nodes) from the NoC */
	int nodesNumber;

	/**
	 * the size of the 2D mesh, sqrt(nodesNumber) (sqrt(nodesNumber) * sqrt(nodesNumber)
	 * = nodesNumber)
	 */
	int edgeSize;

	/**
	 * the number of processes (tasks). Note that each core has only one task
	 * associated to it.
	 */
	int coresNumber;

	/** counts how many cores were parsed from the parsed APCGs */
	private int previousCoreCount = 0;
	
	/**
	 * the number of links from the NoC
	 */
	int linksNumber;

	/** the tiles from the Network-on-Chip (NoC) */
	NodeType[] nodes;

	/** the processes (tasks, cores) */
	Core[] cores;

	/** the communication channels from the NoC */
	LinkType[] links;

	/**
	 * what links are used by tiles to communicate (each source - destination
	 * tile pair has a list of link IDs). The matrix must have size
	 * <tt>nodesNumber x nodesNumber</tt>. <b>This must be <tt>null</tt> when
	 * <tt>buildRoutingTable</tt> is <tt>true</tt> </b>
	 */
	List<Integer>[][] linkUsageList = null;
	
	/** the seed for the random number generator of the initial population */
	private Long seed;

	/**
	 * whether or not to build routing table too. When the SA algorithm builds
	 * the routing table, the mapping process takes more time but, this should
	 * yield better performance
	 */
	boolean buildRoutingTable;

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
	LegalTurnSet legalTurnSet;

	/**
	 * the routing effort
	 * 
	 * @see RoutingEffort
	 */
	RoutingEffort routingEffort;

	/** the link bandwidth */
	double linkBandwidth;
	
	/** the energy consumption per bit read */
	private float bufReadEBit;
	
	/** the energy consumption per bit write */
	private float bufWriteEBit;
	
	/** the size of the Priority Queue */
	private int priorityQueueSize;

	/** minimum hit threshold */
	private int minHitThreshold;

	/**
	 * the processes matrix holds the sum of the communication volumes from two
	 * processes (both ways)
	 */
	long[][] procMatrix = null;

	/**
	 * the NoC architecture's matrix holds the energy required to transfer data
	 * from one node to another
	 */
	float[][] archMatrix = null;

	/**
	 * procMapArray[i] represents the actual process that the i-th mapped
	 * process corresponds to
	 */
	int[] procMapArray = null;

	static final float MAX_VALUE = Integer.MAX_VALUE - 100;

	/**
	 * each newly mapped communication transaction should be less than this
	 * value. Useful for non-regular region mapping
	 */
	static final float MAX_PER_TRAN_COST = MAX_VALUE;

	/** the minimum upper bound */
	private float minUpperBound;

	/** counts how many times the upper bound was computed */
	private int minUpperBoundHitCount;

	private boolean insertAllFlag;

	private int previousInsert;

	/** the current minimum cost of the mapping */
	private float minCost;

	/** the best mapping */
	private MappingNode bestMapping;

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
	
	static enum TopologyParameter {
		/** on what row of a 2D mesh the node is located */
		ROW,
		/** on what column of a 2D mesh the node is located */
		COLUMN,
	};
	
	private static final String LINK_IN = "in";
	
	private static final String LINK_OUT = "out";
	
	private Integer[] nodeRows;
	
	private Integer[] nodeColumns;
	
	String getNodeTopologyParameter(NodeType node,
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
	int[][][] routingTables;
	
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
	 * Constructor
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
	 * @param priorityQueueSize
	 *            the size of the priority queue
	 * @param bufReadEBit
	 *            energy consumption per bit read
	 * @param bufWriteEBit
	 *            energy consumption per bit write
	 * @param switchEBit
	 *            the energy consumed for switching one bit of data
	 * @param linkEBit
	 *            the energy consumed for sending one data bit
	 * @param seed
	 *            the seed for the random number generator of the initial
	 *            population
	 * @throws JAXBException
	 * @throws TooFewNocNodesException
	 */
	public BranchAndBoundMapper(String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir,
			int coresNumber, double linkBandwidth, int priorityQueueSize,
			float bufReadEBit, float bufWriteEBit, float switchEBit,
			float linkEBit, Long seed) throws JAXBException, TooFewNocNodesException {
		this(benchmarkName, ctgId, apcgId, topologyName, topologySize, topologyDir,
				coresNumber, linkBandwidth, priorityQueueSize, false,
				LegalTurnSet.WEST_FIRST, bufReadEBit, bufWriteEBit, switchEBit,
				linkEBit, seed);
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
	 * @param priorityQueueSize
	 *            the size of the priority queue
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
	 * @param seed
	 *            the seed for the random number generator of the initial
	 *            population
	 * @throws JAXBException
	 * @throws TooFewNocNodesException 
	 */
	public BranchAndBoundMapper(String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir,
			int coresNumber, double linkBandwidth, int priorityQueueSize,
			boolean buildRoutingTable, LegalTurnSet legalTurnSet,
			float bufReadEBit, float bufWriteEBit, float switchEBit,
			float linkEBit, Long seed) throws JAXBException, TooFewNocNodesException {
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
		this.priorityQueueSize = priorityQueueSize;
		this.buildRoutingTable = buildRoutingTable;
		this.legalTurnSet = legalTurnSet;
		this.bufReadEBit = bufReadEBit;
		this.bufWriteEBit = bufWriteEBit;
		this.seed = seed;

		initializeNocTopology(topologyDir, switchEBit, linkEBit);
		initializeCores();
		if (this.nodesNumber < this.coresNumber) {
			throw new TooFewNocNodesException(this.coresNumber, this.nodesNumber);
		}
	}
	
	@Override
	public String getMapperId() {
		return MAPPER_ID;
	}
	
	private void initializeCores() {
		cores = new Core[coresNumber];
		for (int i = 0; i < cores.length; i++) {
			cores[i] = new Core(i, null,  -1);
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
	 * @param topologyDir
	 *            the topology directory
	 * @param switchEBit
	 *            the energy consumed for switching a bit of data
	 * @param linkEBit
	 *            the energy consumed for sending a data bit
	 * @throws JAXBException 
	 */
	private void initializeNocTopology(File topologyDir, float switchEBit, float linkEBit) throws JAXBException {
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

			// Now build the g_link_usage_list
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
		Random rand;
		if (seed == null) {
			rand = new Random();
		} else {
			rand = new Random(seed);
		}
		for (int i = 0; i < coresNumber; i++) {
			int k = Math.abs(rand.nextInt()) % nodesNumber;
			while (!Integer.toString(-1).equals(nodes[k].getCore())) {
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

	/**
	 * Initialization function for Branch-and-Bound mapping
	 */
	private void init() {
		if (logger.isDebugEnabled()) {
			logger.debug("Initialize for branch-and-bound");
		}
		procMapArray = new int[coresNumber];
		sortProcesses();
		buildProcessMatrix();
		buildArchitectureMatrix();

		// if (exist_non_regular_regions()) {
		// // let's calculate the maximum ebit of sending a bit
		// // from one tile to its neighboring tile
		// float max_e = -1;
		// for (int i = 0; i < linksNumber; i++) {
		// if (links[i].getCost() > max_e)
		// max_e = links[i].getCost();
		// }
		// float eb = max_e;
		// max_e = -1;
		// for (int i = 0; i < nodesNumber; i++) {
		// if (nodes[i].getCost() > max_e)
		// max_e = nodes[i].getCost();
		// }
		// eb += max_e * 2;
		// MAX_PER_TRAN_COST = eb * DUMMY_VOL * 1.3; // let's put some overhead
		// }

	}

	private void buildProcessMatrix() {
		procMatrix = new long[coresNumber][coresNumber];
		for (int i = 0; i < coresNumber; i++) {
			int row = cores[i].getRank();
			for (int j = 0; j < coresNumber; j++) {
				int col = cores[j].getRank();
				procMatrix[row][col] = cores[i].getFromCommunication()[j]
						+ cores[i].getToCommunication()[j];
			}
		}
		// Sanity checking
		for (int i = 0; i < coresNumber; i++) {
			for (int j = 0; j < coresNumber; j++) {
				if (procMatrix[i][j] < 0) {
					logger.fatal("Error for < 0");
					System.exit(1);
				}
				if (procMatrix[i][j] != procMatrix[j][i]) {
					logger.fatal("Error. The process matrix is not symetric.");
					System.exit(1);
				}
			}
		}
	}

	private void buildArchitectureMatrix() {
		archMatrix = new float[nodesNumber][nodesNumber];
		for (int src = 0; src < nodesNumber; src++) {
			for (int dst = 0; dst < nodesNumber; dst++) {
				float energy = 0;
				NodeType currentNode = nodes[src];
				energy += currentNode.getCost();
				while (Integer.valueOf(currentNode.getId()) != dst) {
					int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
					LinkType link = links[linkId];
					energy += link.getCost();
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
					energy += currentNode.getCost();
				}
				archMatrix[src][dst] = energy;
			}
		}
		// Sanity checking
		for (int i = 0; i < nodesNumber; i++) {
			for (int j = 0; j < nodesNumber; j++) {
				if (archMatrix[i][j] != archMatrix[j][i]) {
					logger.fatal("Error. The architecture matrix is not symetric.");
					System.exit(1);
				}
			}
		}
	}

	/**
	 * sort the processes so that the branch-and-bound mapping can be
	 * accelerated
	 */
	private void sortProcesses() {
		// sort them according to the sum of each process's ingress and egress
		// communication volume
		for (int i = 0; i < cores.length; i++) {
			cores[i].setTotalCommVol(0);
			for (int k = 0; k < cores.length; k++) {
				cores[i].setTotalCommVol(cores[i].getTotalCommVol()
						+ cores[i].getToCommunication()[k]
						+ cores[i].getFromCommunication()[k]);
			}
		}
		// Now rank them
		int currentRank = 0;
		// locked PEs have the highest priority
		// for (int i=0; i<cores.length; i++) {
		// if (cores[i].isLocked()) {
		// proc_map_array[cur_rank] = i;
		// cores[i].setRank(cur_rank ++);
		// }
		// else {
		// cores[i].setRank(-1);
		// }
		// }
		// the remaining PEs are sorted based on their comm volume
		for (int i = currentRank; i < cores.length; i++) {
			long max = -1;
			int maxid = -1;
			for (int k = 0; k < coresNumber; k++) {
				if (cores[k].getRank() != -1) {
					continue;
				}
				if (cores[k].getTotalCommVol() > max) {
					max = cores[k].getTotalCommVol();
					maxid = k;
				}
			}
			cores[maxid].setRank(i);
			procMapArray[i] = maxid;
		}
	}

	private void branchAndBoundMapping() {
		init();
		minCost = MAX_VALUE;
		minUpperBound = MAX_VALUE;
		PriorityQueue Q = new PriorityQueue();

		// if (exist_locked_pe()) {
		// // this ruins the symmetric structure of the system completely.
		// // although for some corner cases, symmetry still exists, we don't
		// // consider it here.
		// for (int i = 0; i < edgeSize; i++) {
		// for (int j = 0; j < edgeSize; j++) {
		// MappingNode pNode = new MappingNode(i * edgeSize + j);
		// if (!pNode.isIllegal()) {
		// Q.insert(pNode);
		// }
		// }
		// }
		// } else {
		// To exploit the symmetric structure of the system, we only need
		// to map the first processes to one corner of the chip, as shown
		// in the following code.
		// And if we need to synthesize the routing table, then there is not
		// much symmetry property to be exploited
		if (!buildRoutingTable) {
			int size = (edgeSize + 1) / 2;
			for (int i = 0; i < size; i++) {
				for (int j = 0; j <= i; j++) {
					MappingNode pNode = new MappingNode(this, i * edgeSize + j);
					if (!pNode.isIllegal()) {
						Q.insert(pNode);
					}
				}
			}
		} else {
			// for west-first or odd-even, we only need to consider the
			// bottom half
			int size = (edgeSize + 1) / 2;
			for (int i = 0; i < size; i++) {
				for (int j = 0; j < edgeSize; j++) {
					MappingNode pNode = new MappingNode(this, i * edgeSize + j);
					if (!pNode.isIllegal()) {
						Q.insert(pNode);
					}
				}
			}
		}
		// }

		bestMapping = null;
		minUpperBoundHitCount = 0;

		while (!Q.empty()) {
			MappingNode pNode = Q.next();
			if (MathUtils.definitelyGreaterThan(pNode.cost, minCost)
					|| MathUtils.definitelyGreaterThan(pNode.lowerBound, minUpperBound)) {
				continue;
			}

			insertAllFlag = false;
			previousInsert = 0;
			/**********************************************************************
			 * Change this to adjust the tradeoff between the solution quality *
			 * and the run time *
			 **********************************************************************/
			if (Q.length() < priorityQueueSize) {
				insertAll(pNode, Q);
//				System.exit(-1);
				continue;
			} else {
				selectiveInsert(pNode, Q);
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Totally " + MappingNode.cnt + " have been generated");
		}
		if (bestMapping != null) {
			applyMapping(bestMapping);
		} else {
			logger.info("Can not find a suitable solution.");
		}
	}

	private void insertAll(MappingNode pNode, PriorityQueue Q) {
		if (logger.isDebugEnabled()) {
			logger.debug("insertAll cnt " + MappingNode.cnt + " queue length "
					+ Q.length() + " previous insert " + previousInsert
					+ " minUpperBound " + minUpperBound);
		}
		if (MathUtils.approximatelyEqual(pNode.upperBound, minUpperBound)
				&& MathUtils.definitelyLessThan(minUpperBound, MAX_VALUE)
				&& minUpperBoundHitCount <= minHitThreshold) {
			insertAllFlag = true;
		}
		for (int i = previousInsert; i < nodesNumber; i++) {
			if (logger.isTraceEnabled()) {
				logger.trace("Node expandable at " + i + " " + pNode.isExpandable(i));
			}
			if (pNode.isExpandable(i)) {
				MappingNode child = new MappingNode(this, pNode, i, true);
				if (MathUtils.definitelyGreaterThan(child.lowerBound, minUpperBound)
						|| MathUtils.definitelyGreaterThan(child.cost, minCost)
						|| (MathUtils.approximatelyEqual(child.cost, minCost) && bestMapping != null)
						|| child.isIllegal()) {
					;
				} else {
					if (logger.isTraceEnabled()) {
						logger.trace("Child upper upper bound is "
								+ child.upperBound);
					}
					if (MathUtils.definitelyLessThan(child.upperBound, minUpperBound)) {
						minUpperBound = child.upperBound;
						if (logger.isDebugEnabled()) {
							logger.debug("Current minimum cost upper bound is " + minUpperBound);
						}
						minUpperBoundHitCount = 0;

						// some new stuff here: we keep the mapping with
						// min upperBound
						if (buildRoutingTable) {
							if (MathUtils.definitelyLessThan(child.upperBound, minCost)) {
								bestMapping = new MappingNode(this, child);
								minCost = child.upperBound;
							} else if (MathUtils.definitelyLessThan(child.upperBound, minUpperBound)
									&& bestMapping != null)
								bestMapping = new MappingNode(this, child);
						}
					}
					if (child.getStage() == coresNumber) {
						minCost = child.cost;
						if (child.getStage() < coresNumber)
							minCost = child.upperBound;
						if (MathUtils.definitelyLessThan(minCost, minUpperBound))
							minUpperBound = minCost;
						if (logger.isDebugEnabled()) {
							logger.debug("Current minimum cost is " + minCost);
						}
						bestMapping = child;
					} else {
						Q.insert(child);
						if (Q.length() >= priorityQueueSize && !insertAllFlag) {
							previousInsert = i;
							selectiveInsert(pNode, Q);
							return;
						}
					}
				}
			}
		}
	}

	private void selectiveInsert(MappingNode pNode, PriorityQueue Q) {
		if (logger.isDebugEnabled()) {
			logger.debug("selectiveInsert " + MappingNode.cnt + " " + Q.length());
		}
		if ((MathUtils.approximatelyEqual(Math.abs(pNode.upperBound - minUpperBound), 0.01f))
				&& MathUtils.definitelyLessThan(minUpperBound, MAX_VALUE)
				&& minUpperBoundHitCount <= minHitThreshold) {
			minUpperBoundHitCount++;
			insertAll(pNode, Q);
			return;
		}
		// In this case, we only select one child which has the
		// smallest partial cost. However, if the node is currently
		// the one with the minUpperBound, then its child which
		// is generated by the corresponding minUpperBound is
		// also generated
		int index = pNode.bestCostCandidate();
		MappingNode child = new MappingNode(this, pNode, index, true);
		if (MathUtils.definitelyGreaterThan(child.lowerBound, minUpperBound)
				|| MathUtils.definitelyGreaterThan(child.cost, minCost)
				|| (MathUtils.approximatelyEqual(child.cost, minCost) && bestMapping != null)) {
			return;
		}
		else {
			if (MathUtils.definitelyLessThan(child.upperBound, minUpperBound - 0.01f)) {
				// In this case, we should also insert other children
				insertAllFlag = true;
				insertAll(pNode, Q);
				return;
			}
			if (child.getStage() == coresNumber || MathUtils.approximatelyEqual(child.lowerBound, child.upperBound)) {
				minCost = child.cost;
				if (child.getStage() < coresNumber) {
					minCost = child.upperBound;
				}
				if (MathUtils.definitelyLessThan(minCost, minUpperBound)) {
					minUpperBound = minCost;
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Current minimum cost is " + minCost);
				}
				bestMapping = child;
			} else {
				Q.insert(child);
			}
		}

		if (MathUtils.definitelyGreaterThan(pNode.upperBound, minUpperBound)
				|| MathUtils.approximatelyEqual(pNode.upperBound, MAX_VALUE)) {
			return;
		}

		if (index == pNode.bestUpperBoundCandidate()) {
			return;
		}

		index = pNode.bestUpperBoundCandidate();
		if (!pNode.isExpandable(index)) {
			logger.fatal("Error in expanding at stage " + pNode.getStage());
			logger.fatal("index = " + index);
			System.exit(-1);
		}
		child = new MappingNode(this, pNode, index, true);
		if (MathUtils.definitelyGreaterThan(child.lowerBound, minUpperBound)
				|| MathUtils.definitelyGreaterThan(child.cost, minCost)) {
			return;
		}
		else {
			if (MathUtils.definitelyLessThan(child.upperBound, minUpperBound)) {
				minUpperBound = child.upperBound;
				if (logger.isDebugEnabled()) {
				logger.debug("Current minimum cost upper bound is " + minUpperBound);
				}
				minUpperBoundHitCount = 0;
			}
			if (child.getStage() == coresNumber
					|| MathUtils.approximatelyEqual(child.lowerBound, child.upperBound)) {
				if (MathUtils.approximatelyEqual(minCost, child.cost) && bestMapping != null) {
					return;
				}
				else {
					minCost = child.cost;
					if (child.getStage() < coresNumber) {
						minCost = child.upperBound;
					}
					if (MathUtils.definitelyLessThan(minCost, minUpperBound)) {
						minUpperBound = minCost;
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Current minimum cost is " + minCost);
					}
					bestMapping = child;
				}
			} else {
				Q.insert(child);
			}
		}
	}

	private void applyMapping(MappingNode bestMapping) {
		for (int i = 0; i < coresNumber; i++) {
			cores[i].setNodeId(-1);
		}
		for (int i = 0; i < nodesNumber; i++) {
			nodes[i].setCore(Integer.toString(-1));
		}
		for (int i = 0; i < coresNumber; i++) {
			int procId = procMapArray[i];
			cores[procId].setNodeId(bestMapping.mapToNode(i));
			nodes[bestMapping.mapToNode(i)].setCore(Integer.toString(procId));
		}
		if (buildRoutingTable) {
			bestMapping.programRouters();
		}
	}

	private void saveRoutingTables() {
		if (logger.isDebugEnabled()) {
			logger.debug("Saving the routing tables");
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
				File file = new File(topologyDir + File.separator + "bb" + routing
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
				File file = new File(topologyDir + File.separator + "bb" + routing
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
	
	@Override
	public String map() throws TooFewNocNodesException {
		if (nodesNumber < coresNumber) {
			throw new TooFewNocNodesException(coresNumber, nodesNumber);
		}

		mapCoresToNocNodesRandomly();

		if (logger.isDebugEnabled()) {
			printCurrentMapping();
		}
		
		if (coresNumber == 1) {
			logger.info("Branch and Bound will not start for mapping a single core. This core simply mapped randomly.");
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
			branchAndBoundMapping();
		}
		
		long userEnd = TimeUtils.getUserTime();
		long sysEnd = TimeUtils.getSystemTime();
		long realEnd = System.nanoTime();
		monitor.stopMonitor();
		logger.info("Mapping process finished successfully.");
		logger.info("Time: " + (realEnd - realStart) / 1e9 + " seconds");
		logger.info("Memory: " + monitor.getAverageUsedHeap()
				/ (1024 * 1024 * 1.0) + " MB");

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
				"Branch and Bound", benchmarkId, apcgId, nocTopologyId,
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
					logger.error("The node from line '" + line
							+ "' is not a number", e);
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
					logger.error("The destination from line '" + line
							+ "' is not a number", e);
				}
				double rate = 0;
				try {
					rate = Double.valueOf(substring.substring(substring
							.indexOf("\t") + 1));
					if (logger.isTraceEnabled()) {
						logger.trace(" rate = " + rate);
					}
				} catch (NumberFormatException e) {
					logger.error("The rate from line '" + line
							+ "' is not a number", e);
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
	
	private void printCores() {
		for (int i = 0; i < cores.length; i++) {
			System.out.println("Core " + cores[i].getCoreId() + "(node "
					+ cores[i].getNodeId() + ", rank " + cores[i].getRank()
					+ ")");
			
			long[] toCommunication = cores[i].getToCommunication();
			System.out.println("to communication");
			for (int j = 0; j < toCommunication.length; j++) {
				System.out.println(toCommunication[j]);
			}
			
			long[] fromCommunication = cores[i].getFromCommunication();
			System.out.println("from communication");
			for (int j = 0; j < fromCommunication.length; j++) {
				System.out.println(fromCommunication[j]);
			}
			
			long[] toBandwidthRequirement = cores[i].getToBandwidthRequirement();
			System.out.println("to bandwidth requirement");
			for (int j = 0; j < toBandwidthRequirement.length; j++) {
				System.out.println(toBandwidthRequirement[j]);
			}
			
			long[] fromBandwidthRequirement = cores[i].getFromBandwidthRequirement();
			System.out.println("from bandwidth requirement");
			for (int j = 0; j < fromBandwidthRequirement.length; j++) {
				System.out.println(fromBandwidthRequirement[j]);
			}
		}
		
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
	        	logger.info("Link " + i + " is overloaded: " + usedBandwidth[i] + " > "
	                 + links[i].getBandwidth());
	            violations ++;
	        }
	    }
		return violations == 0;
	}
	
	/**
	 * Computes the communication energy as switch energy + link energy + buffer energy.
	 * 
	 * @see #calculateSwitchEnergy()
	 * @see #calculateLinkEnergy()
	 * @see #calculateBufferEnergy()
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
									+ dstProc + ") current tile "
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
										+ " to core " + dstProc + ") current tile "
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
	
	public static void main(String[] args) throws TooFewNocNodesException,
			IOException, JAXBException {
		final int applicationBandwithRequirement = 3; // a multiple of the communication volume
		final double linkBandwidth = 256E9;
		final int priorityQueueSize = 2000;
		final float switchEBit = 0.284f;
		final float linkEBit = 0.449f;
		final float bufReadEBit = 1.056f;
		final float bufWriteEBit = 2.831f;
		
		MapperInputProcessor mapperInputProcessor = new MapperInputProcessor() {
			
			@Override
			public void useMapper(String benchmarkFilePath,
					String benchmarkName, String ctgId, String apcgId,
					List<CtgType> ctgTypes, List<ApcgType> apcgTypes,
					boolean doRouting, Long seed) throws JAXBException,
					TooFewNocNodesException, FileNotFoundException {
				logger.info("Using a Branch and Bound mapper for "
						+ benchmarkFilePath + "ctg-" + ctgId + " (APCG " + apcgId + ")");
				
				BranchAndBoundMapper bbMapper;
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
						"priorityQueueSize",
						"switchEBit",
						"linkEBit",
						"bufReadEBit",
						"bufWriteEBit",
						"routing"};
				String values[] = new String[] {
						Integer.toString(applicationBandwithRequirement),
						Double.toString(linkBandwidth),
						Integer.toString(priorityQueueSize),
						Float.toString(switchEBit), Float.toString(linkEBit),
						Float.toString(bufReadEBit),
						Float.toString(bufWriteEBit),
						null};
				if (doRouting) {
					values[values.length - 1] = "true";
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// Branch and Bound with routing
					bbMapper = new BranchAndBoundMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							priorityQueueSize, true,
							LegalTurnSet.ODD_EVEN, bufReadEBit,
							bufWriteEBit, switchEBit, linkEBit, seed);
				} else {
					values[values.length - 1] = "false";
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// Branch and Bound without routing
					bbMapper = new BranchAndBoundMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							priorityQueueSize, bufReadEBit,
							bufWriteEBit, switchEBit, linkEBit, seed);
				}
	
	//			// read the input data from a traffic.config file (NoCmap style)
	//			bbMapper.parseTrafficConfig(
	//					"telecom-mocsyn-16tile-selectedpe.traffic.config",
	//					linkBandwidth);
				
				for (int k = 0; k < apcgTypes.size(); k++) {
					// read the input data using the Unified Framework's XML interface
					bbMapper.parseApcg(apcgTypes.get(k), ctgTypes.get(k), applicationBandwithRequirement);
				}
				
	//			// This is just for checking that bbMapper.parseTrafficConfig(...)
	//			// and parseApcg(...) have the same effect
//				bbMapper.printCores();
	
				String mappingXml = bbMapper.map();
				File dir = new File(benchmarkFilePath + "ctg-" + ctgId);
				dir.mkdirs();
				String routing = "";
				if (doRouting) {
					routing = "_routing";
				}
				String mappingXmlFilePath = benchmarkFilePath + "ctg-" + ctgId
						+ File.separator + "mapping-" + apcgId + "_"
						+ bbMapper.getMapperId() + routing + ".xml";
				PrintWriter pw = new PrintWriter(mappingXmlFilePath);
				logger.info("Saving the mapping XML file" + mappingXmlFilePath);
				pw.write(mappingXml);
				pw.close();
	
				logger.info("The generated mapping is:");
				bbMapper.printCurrentMapping();
				
				bbMapper.analyzeIt();
				
			}
		};
		mapperInputProcessor.processInput(args);
	}

}
