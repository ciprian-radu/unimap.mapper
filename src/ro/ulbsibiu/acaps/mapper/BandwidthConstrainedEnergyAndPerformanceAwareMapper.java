package ro.ulbsibiu.acaps.mapper;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
import ro.ulbsibiu.acaps.mapper.sa.Core;
import ro.ulbsibiu.acaps.mapper.util.HeapUsageMonitor;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.mapper.util.TimeUtils;
import ro.ulbsibiu.acaps.noc.xml.link.LinkType;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;
import ro.ulbsibiu.acaps.noc.xml.node.ObjectFactory;
import ro.ulbsibiu.acaps.noc.xml.node.RoutingTableEntryType;
import ro.ulbsibiu.acaps.noc.xml.node.TopologyParameterType;

/**
 * Skeleton for a bandwidth constrained energy- and performance-aware NoC
 * application mapping algorithm. Such a {@link Mapper} is capable of evaluating
 * mappings using the analytical energy bit model from [2]. It is also able to
 * generate routing functions, like in [2]. The generated mappings can also be
 * verified if they respect bandwidth constraints.
 * <p>
 * <b>Notes: </b>
 * <ol>
 * <li>applies only to 2d mesh topologies</li>
 * </ol>
 * </p>
 * 
 * <ul>
 * <li>
 * [1] J. Hu and R. Marculescu, “Energy-aware mapping for tile-based NoC
 * architectures under performance constraints,” in Proceedings of the 2003 Asia
 * and South Pacific Design Automation Conference, pp. 233-239, 2003.</li>
 * <li>
 * [2] J. Hu and R. Marculescu, “Energy- and performance-aware mapping for
 * regular NoC architectures,” IEEE TRANSACTIONS ON COMPUTER-AIDED DESIGN OF
 * INTEGRATED CIRCUITS AND SYSTEMS, vol. 24, no. 4, pp. 551--562, 2005.</li>
 * </ul>
 * 
 * 
 * @author cradu
 * 
 */
public abstract class BandwidthConstrainedEnergyAndPerformanceAwareMapper
		implements Mapper {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(BandwidthConstrainedEnergyAndPerformanceAwareMapper.class);

	/**
	 * how costly is each unit of link overload (a link is overloaded when it
	 * has to send more bits/s than its bandwidth)
	 */
	private final float OVERLOAD_UNIT_COST = 1000000000;
	
	protected static final int NORTH = 0;

	protected static final int SOUTH = 1;

	protected static final int EAST = 2;

	protected static final int WEST = 3;
	
	protected static enum TopologyParameter {
		/** on what row of a 2D mesh the node is located */
		ROW,
		/** on what column of a 2D mesh the node is located */
		COLUMN,
	};
	
	private static final String LINK_IN = "in";
	
	private static final String LINK_OUT = "out";
	
	/** energy consumption per bit read */
	private float bufReadEBit;

	/** energy consumption per bit write */
	private float bufWriteEBit;
	
	/** energy consumed by a router when it switches a bit */
	private float switchEBit;
	
	/** energy consumed by a link when it transports a bit */
	private float linkEBit;

	/**
	 * whether or not to build routing table too. When the SA algorithm builds
	 * the routing table, the mapping process takes more time but, this should
	 * yield better performance
	 */
	protected boolean buildRoutingTable;
	
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
	protected LegalTurnSet legalTurnSet;
	
	/** the benchmark's name */
	protected String benchmarkName;
	
	/** the CTG ID */
	protected String ctgId;
	
	/** the ACPG ID */
	protected String apcgId;
	
	/** the topology name */
	protected String topologyName;
	
	/** the directory where the NoC topology is described */
	protected File topologyDir;
	
	/** the topology size */
	protected String topologySize;
	
	/** the nodes from the Network-on-Chip (NoC) */
	protected NodeType[] nodes;
	
	/** the link bandwidth */
	protected double linkBandwidth;
	
	/** the communication channels from the NoC */
	protected ro.ulbsibiu.acaps.noc.xml.link.LinkType[] links;
	
	/**
	 * what links are used by nodes to communicate (each source - destination
	 * node pair has a list of link IDs). The matrix must have size
	 * <tt>nodes.length x nodes.length</tt>. <b>This must be <tt>null</tt> when
	 * <tt>buildRoutingTable</tt> is <tt>true</tt> </b>
	 */
	protected List<Integer>[][] linkUsageList = null;
	
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
	
	/** the number of mesh nodes placed horizontally */
	protected int hSize;
	
	/** the processes (tasks, cores) */
	protected Core[] cores;
	
	/** counts how many cores were parsed from the parsed APCGs */
	private int previousCoreCount = 0;
	
	/** routingTables[nodeId][sourceNode][destinationNode] = link ID */
	protected int[][][] routingTables;
	
	/** holds the generated routing table */
	private int[][][][] generatedRoutingTable = null;
	
	private Integer[] nodeRows;
	
	private Integer[] nodeColumns;
	
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
	public BandwidthConstrainedEnergyAndPerformanceAwareMapper(
			String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir,
			int coresNumber, double linkBandwidth, float switchEBit,
			float linkEBit) throws JAXBException {
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
	public BandwidthConstrainedEnergyAndPerformanceAwareMapper(
			String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir,
			int coresNumber, double linkBandwidth, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit,
			float switchEBit, float linkEBit) throws JAXBException {
		if (topologyDir == null) {
			logger.error("Please specify the NoC topology directory! Stopping...");
			System.exit(0);
		}
		if (!topologyDir.isDirectory()) {
			logger.error("The specified NoC topology directory does not exist or is not a directory! Stopping...");
			System.exit(0);
		}
		
		this.benchmarkName = benchmarkName;
		this.ctgId = ctgId;
		this.apcgId = apcgId;
		this.topologyName = topologyName;
		this.topologySize = topologySize;
		this.topologyDir = topologyDir;
		this.cores = new Core[coresNumber];
		this.linkBandwidth = linkBandwidth;
		logger.info("NoC links' bandwidth is " + this.linkBandwidth);
		this.buildRoutingTable = buildRoutingTable;
		this.legalTurnSet = legalTurnSet;
		if (this.buildRoutingTable) {
			logger.info("Generating routing function using " + legalTurnSet + " legal turn set");
		} else {
			logger.info("Assuming XY routing function");
		}
		this.bufReadEBit = bufReadEBit;
		this.bufWriteEBit = bufWriteEBit;

		initializeNocTopology(switchEBit, linkEBit);
		initializeCores();
	}
	
	private void initializeCores() {
		for (int i = 0; i < cores.length; i++) {
			cores[i] = new Core(i, null, -1);
			cores[i].setFromCommunication(new long[cores.length]);
			cores[i].setToCommunication(new long[cores.length]);
			cores[i].setFromBandwidthRequirement(new long[cores.length]);
			cores[i].setToBandwidthRequirement(new long[cores.length]);
		}
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
		nodes = new NodeType[nodeXmls.length];
		nodeRows = new Integer[nodes.length];
		nodeColumns = new Integer[nodes.length];
		try {
			this.hSize = Integer.valueOf(topologySize.substring(0, topologySize.lastIndexOf("x")));
		} catch (NumberFormatException e) {
			logger.fatal("Could not determine the size of the 2D mesh! Stopping...", e);
			System.exit(0);
		}
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
		links = new LinkType[linkXmls.length];
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
		routingTables = new int[nodes.length][nodes.length][nodes.length];
		generateXYRoutingTable();

		generateLinkUsageList();
	}
	
	protected void generateLinkUsageList() {
		if (this.buildRoutingTable == true) {
			linkUsageList = null;
		} else {
			// Allocate the space for the link usage table
			int[][][] linkUsageMatrix = new int[nodes.length][nodes.length][links.length];

			// Setting up the link usage matrix
			for (int srcId = 0; srcId < nodes.length; srcId++) {
				for (int dstId = 0; dstId < nodes.length; dstId++) {
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
			linkUsageList = new ArrayList[nodes.length][nodes.length];
			for (int src = 0; src < nodes.length; src++) {
				for (int dst = 0; dst < nodes.length; dst++) {
					linkUsageList[src][dst] = new ArrayList<Integer>();
					if (src == dst) {
						continue;
					}
					for (int linkId = 0; linkId < links.length; linkId++) {
						if (linkUsageMatrix[src][dst][linkId] == 1) {
							linkUsageList[src][dst].add(linkId);
						}
					}
				}
			}

			logger.assertLog(this.linkUsageList != null, null);
			logger.assertLog(linkUsageList.length == nodes.length, null);
			for (int i = 0; i < linkUsageList.length; i++) {
				logger.assertLog(linkUsageList[i].length == nodes.length, null);
			}
		}
	}
	
	protected String getNodeTopologyParameter(NodeType node,
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
	
	public void generateXYRoutingTable() {
		for (int n = 0; n < nodes.length; n++) {
			NodeType node = nodes[n];
			for (int i = 0; i < nodes.length; i++) {
				for (int j = 0; j < nodes.length; j++) {
					routingTables[Integer.valueOf(node.getId())][i][j] = -2;
				}
			}
	
			for (int dstNode = 0; dstNode < nodes.length; dstNode++) {
				if (dstNode == Integer.valueOf(node.getId())) { // deliver to me
					routingTables[Integer.valueOf(node.getId())][0][dstNode] = -1;
				} else {
					// check out the dst Node's position first
					int dstRow = dstNode / hSize;
					int dstCol = dstNode % hSize;
		
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
			for (int i = 1; i < nodes.length; i++) {
				for (int j = 0; j < nodes.length; j++) {
					routingTables[Integer.valueOf(node.getId())][i][j] = routingTables[Integer.valueOf(node.getId())][0][j];
				}
			}
		}
	}

	/**
	 * Computes the communication energy
	 * 
	 * @return the communication energy
	 */
	protected double calculateCommunicationEnergy() {
		double switchEnergy = calculateSwitchEnergy();
		double linkEnergy = calculateLinkEnergy();
		double bufferEnergy = calculateBufferEnergy();
		if (logger.isTraceEnabled()) {
			logger.trace("switch energy " + switchEnergy);
			logger.trace("link energy " + linkEnergy);
			logger.trace("buffer energy " + bufferEnergy);
		}
		return switchEnergy + linkEnergy + bufferEnergy;
	}

	protected double calculateSwitchEnergy() {
		double energy = 0;
		for (int src = 0; src < nodes.length; src++) {
			for (int dst = 0; dst < nodes.length; dst++) {
				int srcProc = Integer.valueOf(nodes[src].getCore());
				int dstProc = Integer.valueOf(nodes[dst].getCore());
				if (srcProc > -1 && dstProc > -1) {
					long commVol = cores[srcProc].getToCommunication()[dstProc];
					if (commVol > 0) {
						energy += nodes[src].getCost() * commVol;
						NodeType currentNode = nodes[src];
						if (logger.isTraceEnabled()) {
							logger.trace("adding " + currentNode.getCost()
									+ " * " + commVol + " (core " + srcProc
									+ " to core " + dstProc + ") current node "
									+ currentNode.getId());
						}
						while (Integer.valueOf(currentNode.getId()) != dst) {
							int linkId = routingTables[Integer
									.valueOf(currentNode.getId())][src][dst];
							LinkType link = links[linkId];
							String node = "-1";
							// we work with with bidirectional links
							if (currentNode.getId().equals(link.getFirstNode())) {
								node = link.getSecondNode();
							} else {
								if (currentNode.getId().equals(
										link.getSecondNode())) {
									node = link.getFirstNode();
								}
							}
							currentNode = nodes[Integer.valueOf(node)];
							energy += currentNode.getCost() * commVol;
							if (logger.isTraceEnabled()) {
								logger.trace("adding " + currentNode.getCost()
										+ " * " + commVol + " (core " + srcProc
										+ " to core " + dstProc
										+ ") current node "
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

	protected double calculateLinkEnergy() {
		double energy = 0;
		for (int src = 0; src < nodes.length; src++) {
			for (int dst = 0; dst < nodes.length; dst++) {
				int srcProc = Integer.valueOf(nodes[src].getCore());
				int dstProc = Integer.valueOf(nodes[dst].getCore());
				if (srcProc > -1 && dstProc > -1) {
					long commVol = cores[srcProc].getToCommunication()[dstProc];
					if (commVol > 0) {
						NodeType currentNode = nodes[src];
						while (Integer.valueOf(currentNode.getId()) != dst) {
							int linkId = routingTables[Integer
									.valueOf(currentNode.getId())][src][dst];
							energy += links[linkId].getCost() * commVol;
							LinkType link = links[linkId];
							String node = "-1";
							// we work with with bidirectional links
							if (currentNode.getId().equals(link.getFirstNode())) {
								node = link.getSecondNode();
							} else {
								if (currentNode.getId().equals(
										link.getSecondNode())) {
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

	protected double calculateBufferEnergy() {
		double energy = 0;
		for (int src = 0; src < nodes.length; src++) {
			for (int dst = 0; dst < nodes.length; dst++) {
				int srcProc = Integer.valueOf(nodes[src].getCore());
				int dstProc = Integer.valueOf(nodes[dst].getCore());
				if (srcProc > -1 && dstProc > -1) {
					long commVol = cores[srcProc].getToCommunication()[dstProc];
					if (commVol > 0) {
						NodeType currentNode = nodes[src];
						while (Integer.valueOf(currentNode.getId()) != dst) {
							int linkId = routingTables[Integer
									.valueOf(currentNode.getId())][src][dst];
							energy += (bufReadEBit + bufWriteEBit) * commVol;
							LinkType link = links[linkId];
							String node = "-1";
							// we work with with bidirectional links
							if (currentNode.getId().equals(link.getFirstNode())) {
								node = link.getSecondNode();
							} else {
								if (currentNode.getId().equals(
										link.getSecondNode())) {
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
	 * Calculate the total cost in terms of the sum of the energy consumption
	 * and the penalty of the link overloading
	 * 
	 * @return the total cost
	 */
	protected double calculateTotalCost() {
		// the communication energy part
		double energyCost = calculateCommunicationEnergy();
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
		double cost = energyCost + overloadCost;
		if (logger.isDebugEnabled()) {
			logger.debug("Computed a total cost of " + cost);
		}
		return cost;
	}

	/**
	 * Computes the overload of the links when no routing is performed
	 * 
	 * @return the overload
	 */
	private float calculateOverloadWithFixedRouting() {
		Arrays.fill(linkBandwidthUsage, 0);
		for (int proc1 = 0; proc1 < cores.length; proc1++) {
			for (int proc2 = proc1 + 1; proc2 < cores.length; proc2++) {
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
		for (int i = 0; i < links.length; i++) {
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
		for (int i = 0; i < nodes.length / hSize; i++) {
			for (int j = 0; j < hSize; j++) {
				Arrays.fill(synLinkBandwithUsage[i][j], 0);
			}
		}

		for (int src = 0; src < cores.length; src++) {
			for (int dst = 0; dst < cores.length; dst++) {
				int node1 = cores[src].getNodeId();
				int node2 = cores[dst].getNodeId();
				if (cores[src].getToBandwidthRequirement()[dst] > 0) {
					routeTraffic(node1, node2,
							cores[src].getToBandwidthRequirement()[dst]);
				}
			}
		}

		for (int i = 0; i < nodes.length / hSize; i++) {
			for (int j = 0; j < hSize; j++) {
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
				generatedRoutingTable[row][col][srcNode][dstNode] = direction;
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
		for (linkId = 0; linkId < links.length; linkId++) {
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
		if (linkId == links.length) {
			logger.fatal("Error in locating link");
			System.exit(-1);
		}
		return linkId;
	}
	
	protected void programRouters() {
		// clean all the old routing table
		for (int nodeId = 0; nodeId < nodes.length; nodeId++) {
			for (int srcNode = 0; srcNode < nodes.length; srcNode++) {
				for (int dstNode = 0; dstNode < nodes.length; dstNode++) {
					if (nodeId == dstNode) {
						routingTables[Integer.valueOf(nodes[nodeId].getId())][srcNode][dstNode] = -1;
					} else {
						routingTables[Integer.valueOf(nodes[nodeId].getId())][srcNode][dstNode] = -2;
					}
				}
			}
		}

		for (int row = 0; row < nodes.length / hSize; row++) {
			for (int col = 0; col < hSize; col++) {
				int nodeId = row * hSize + col;
				for (int srcNode = 0; srcNode < nodes.length; srcNode++) {
					for (int dstNode = 0; dstNode < nodes.length; dstNode++) {
						int linkId = locateLink(row, col,
								generatedRoutingTable[row][col][srcNode][dstNode]);
						if (linkId != -1) {
							routingTables[Integer.valueOf(nodes[nodeId].getId())][srcNode][dstNode] = linkId;
						}
					}
				}
			}
		}
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
	
	/**
	 * Performs an analysis of the mapping. It verifies if bandwidth
	 * requirements are met and computes the link, switch and buffer energy.
	 * The communication energy is also computed (as a sum of the three energy
	 * components).
	 */
	public void analyzeIt() {
	    logger.info("Verify the communication load of each link...");
	    String bandwidthRequirements;
	    
		generateLinkUsageList();

		long[] usedBandwidth = new long[links.length];
		
		for (int i = 0; i < links.length; i++) {
			usedBandwidth[i] = 0;
		}

		for (int src = 0; src < nodes.length; src++) {
			for (int dst = 0; dst < nodes.length; dst++) {
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
	    long maxBandwidthRequirement = Long.MIN_VALUE;
		for (int i = 0; i < links.length; i++) {
			logger.info("Link " + i + " has " + links[i].getBandwidth() + " b/s bandwidth");
			logger.info("The application requires link " + i + " " + usedBandwidth[i] + " b/s bandwidth");
	        if (usedBandwidth[i] > links[i].getBandwidth()) {
	        	logger.info("Link " + i + " is overloaded: " + usedBandwidth[i] + " b/s > "
	                 + links[i].getBandwidth() + " b/s");
	            violations ++;
	        }
	        if (usedBandwidth[i] > maxBandwidthRequirement) {
	        	maxBandwidthRequirement = usedBandwidth[i];
	        }
	    }
	    
	    if (violations == 0) {
	    	logger.info("Succes");
	    	bandwidthRequirements = "Succes";
	    }
	    else {
	    	logger.info("Fail");
	    	bandwidthRequirements = "Fail";
	    }
	    
	    logger.info("Maximum bandwidth requirement is " + maxBandwidthRequirement + " b/s");
	    
	    if (logger.isDebugEnabled()) {
		    logger.debug("Energy consumption estimation ");
		    logger.debug("(note this are not exact numbers, but serve as a relative energy indication) ");
		    logger.debug("Energy consumed in link is " + calculateLinkEnergy());
		    logger.debug("Energy consumed in switch is " + calculateSwitchEnergy());
		    logger.debug("Energy consumed in buffer is " + calculateBufferEnergy());
	    }
	    double energy = calculateCommunicationEnergy();
	    logger.info("Total communication energy consumption is " + energy);
	    
		MapperDatabase.getInstance().setOutputs(
				new String[] { "bandwidthRequirements", "maxBandwidthRequirement", "energy" },
				new String[] { bandwidthRequirements, Long.toString(maxBandwidthRequirement), Double.toString(energy) });
	}
	
	public List<CommunicationType> getCommunications(CtgType ctg, String sourceTaskId) {
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

	public String getCoreUid(ApcgType apcg, String sourceTaskId) {
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
	 * informs the algorithm about the application's bandwidth requirement. For
	 * each two communicating IP cores, the bandwidth requirement is obtained by
	 * multiplying the CTG period with their communication volume.
	 * 
	 * @param apcg
	 *            the APCG XML
	 * @param ctg
	 *            the CTG XML
	 */
	public void parseApcg(ApcgType apcg, CtgType ctg) {
		logger.assertLog(apcg != null, "The APCG cannot be null");
		logger.assertLog(ctg != null, "The CTG cannot be null");
		
		// we use previousCoreCount to shift the cores from each APCG
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
						double ctgPeriod = ctg.getPeriod();
						long bandwidthRequirement = 0;
						if (MathUtils.definitelyGreaterThan((float) ctgPeriod, 0)) {
							bandwidthRequirement = (long) (communicationType.getVolume() / ctgPeriod);
						}
						cores[previousCoreCount + Integer.valueOf(sourceCoreId)]
								.getToCommunication()[previousCoreCount
								+ Integer.valueOf(destinationCoreId)] = (long) communicationType
								.getVolume();
						cores[previousCoreCount + Integer.valueOf(sourceCoreId)]
								.getToBandwidthRequirement()[previousCoreCount
								+ Integer.valueOf(destinationCoreId)] = bandwidthRequirement;
						cores[previousCoreCount
								+ Integer.valueOf(destinationCoreId)]
								.getFromCommunication()[previousCoreCount
								+ Integer.valueOf(sourceCoreId)] = (long) communicationType
								.getVolume();
						cores[previousCoreCount
								+ Integer.valueOf(destinationCoreId)]
								.getFromBandwidthRequirement()[previousCoreCount
								+ Integer.valueOf(sourceCoreId)] = bandwidthRequirement;
					}
				}
			}
		}
		previousCoreCount += coreList.size();
	}

	/**
	 * Prints the current mapping. The method can be called any time: while the
	 * algorithm runs, or at the end, in which case the final solution is
	 * displayed.
	 */
	public void printCurrentMapping() {
		for (int i = 0; i < nodes.length; i++) {
			String apcg = "";
			if (!"-1".equals(nodes[i].getCore())) {
				apcg = cores[Integer.valueOf(nodes[i].getCore())].getApcgId();
			}
			System.out.println("NoC node " + nodes[i].getId() + " has core "
					+ nodes[i].getCore() + " (APCG " + apcg + ")");
		}
	}
	
	/**
	 * Allows executing some code, right before the mapping is started. Note
	 * that mapping is started only if there are at least two cores to map. This
	 * can be useful to randomly generate an initial mapping.
	 */
	protected abstract void doBeforeMapping ();

	/**
	 * Executes the mapping algorithm. At the end, you are required to save the
	 * obtained mapping in the {@link #nodes} and {@link #cores} data structures
	 * and to set the routing tables ({@link #programRouters()}), if routing is
	 * enabled ({@link #buildRoutingTable}).
	 * 
	 * @return the number of mappings from the solution (1 for single objective, > 1 for multi-objective)
	 */
	protected abstract int doMapping ();

	/**
	 * Allows executing some code, right after the mapping is done (and some
	 * performance statistics are computed) but, before the mapping is saved
	 * into the database.
	 */
	protected abstract void doBeforeSavingMapping ();
	
	@Override
	public String[] map() throws TooFewNocNodesException {
		if (nodes.length < cores.length) {
			throw new TooFewNocNodesException(cores.length, nodes.length);
		}

		if (!buildRoutingTable) {
			linkBandwidthUsage = new int[links.length];
		} else {
			synLinkBandwithUsage = new int[nodes.length / hSize][hSize][4];
			generatedRoutingTable = new int[nodes.length / hSize][hSize][nodes.length][nodes.length];
			for (int i = 0; i < generatedRoutingTable.length; i++) {
				for (int j = 0; j < generatedRoutingTable[i].length; j++) {
					for (int k = 0; k < generatedRoutingTable[i][j].length; k++) {
						for (int l = 0; l < generatedRoutingTable[i][j][k].length; l++) {
							generatedRoutingTable[i][j][k][l] = -2;
						}
					}
				}
			}
		}
		
		doBeforeMapping();
		
		if (cores.length == 1) {
			logger.info(getMapperId() + " will not start for mapping a single core. This core simply mapped randomly.");
		} else {
			logger.info("Start mapping...");

			logger.assertLog((cores.length == ((int) cores.length)), null);

		}
		Date startDate = new Date();
		HeapUsageMonitor monitor = new HeapUsageMonitor();
		monitor.startMonitor();
		long userStart = TimeUtils.getUserTime();
		long sysStart = TimeUtils.getSystemTime();
		long realStart = System.nanoTime();
		
		int totalNumberOfMappings=0;
		if (cores.length > 1) {
			totalNumberOfMappings = doMapping();
		}
		
		long userEnd = TimeUtils.getUserTime();
		long sysEnd = TimeUtils.getSystemTime();
		long realEnd = System.nanoTime();
		monitor.stopMonitor();
		logger.info("Mapping process finished successfully.");
		logger.info("Time: " + (realEnd - realStart) / 1e9 + " seconds");
		logger.info("Memory: " + monitor.getAverageUsedHeap()
				/ (1024 * 1024 * 1.0) + " MB");
		
		StringWriter[] stringWriter = new StringWriter[totalNumberOfMappings];
		for (int i = 0; i < totalNumberOfMappings; i++) {
			stringWriter[i] = new StringWriter();
		}
		
		for (int j = 0; j < totalNumberOfMappings; j++) {
			doBeforeSavingMapping();
			
//			saveRoutingTables();
			
//			saveTopology();
	
			MappingType mapping = new MappingType();
			mapping.setId(getMapperId());
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
			
			ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory mappingFactory = new ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory();
			JAXBElement<MappingType> jaxbElement = mappingFactory.createMapping(mapping);
			try {
				JAXBContext jaxbContext = JAXBContext.newInstance(MappingType.class);
				Marshaller marshaller = jaxbContext.createMarshaller();
				marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
				marshaller.marshal(jaxbElement, stringWriter[j]);
			} catch (JAXBException e) {
				logger.error("JAXB encountered an error", e);
			}
			
			Integer benchmarkId = MapperDatabase.getInstance().getBenchmarkId(benchmarkName, ctgId);
			Integer nocTopologyId = MapperDatabase.getInstance().getNocTopologyId(topologyName, topologySize);
			// TODO add some mechanism to get a description for the algorithm (and insert it into the database)
			MapperDatabase.getInstance().saveMapping(getMapperId(), getMapperId(),
					benchmarkId, apcgId, nocTopologyId, stringWriter[j].toString(),
					startDate, (realEnd - realStart) / 1e9,
					(userEnd - userStart) / 1e9, (sysEnd - sysStart) / 1e9,
					monitor.getAverageUsedHeap(), null);
		}
		String[] returnString = new String [totalNumberOfMappings];
		for (int i = 0; i < totalNumberOfMappings; i++) {
			returnString[i] = stringWriter[i].toString();
		}
		
		return returnString;
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

}
