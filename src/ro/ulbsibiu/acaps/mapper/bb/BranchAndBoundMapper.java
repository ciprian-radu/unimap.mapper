package ro.ulbsibiu.acaps.mapper.bb;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.CoreType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.TaskType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CommunicationType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MapType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MappingType;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.bb.MappingNode.RoutingEffort;
import ro.ulbsibiu.acaps.mapper.sa.Core;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.noc.xml.link.LinkType;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;
import ro.ulbsibiu.acaps.noc.xml.node.ObjectFactory;
import ro.ulbsibiu.acaps.noc.xml.node.RoutingTableEntryType;
import ro.ulbsibiu.acaps.noc.xml.node.TopologyParameterType;
import ro.ulbsibiu.acaps.noc.xml.topologyParameter.TopologyType;

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
	int linkBandwidth;
	
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
	int[][] procMatrix = null;

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

	/** the ID of the NoC topology used by this algorithm */
	private String topologyId;
	
	static enum TopologyParameter {
		/** on what row of a 2D mesh the node is located */
		ROW,
		/** on what column of a 2D mesh the node is located */
		COLUMN,
		/** on what row of a 2D mesh the source node of a link is located */
		ROW_TO,
		/** on what row of a 2D mesh the destination node of a link is located */
		ROW_FROM,
		/** on what column of a 2D mesh the source node of a link is located */
		COLUMN_TO,
		/** on what column of a 2D mesh the destination node of a link is located */
		COLUMN_FROM
	};
	
	private static final String LINK_IN = "in";
	
	private static final String LINK_OUT = "out";
	
	static String getNodeTopologyParameter(NodeType node,
			TopologyParameter parameter) {
		String value = null;
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
		return value;
	}

	private String getLinkTopologyParameter(LinkType link,
			TopologyParameter parameter) {
		String value = null;
		List<ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType> topologyParameters = link
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
						+ "' in the link " + link.getId());
		return value;
	}
	
	/** routingTables[nodeId][sourceNode][destinationNode] = link ID */
	int[][][] routingTables;
	
	public void generateXYRoutingTable(NodeType node, int nodesNumber, int gEdgeSize, LinkType[] links) {
		for (int i = 0; i < nodesNumber; i++) {
			for (int j = 0; j < nodesNumber; j++) {
				routingTables[Integer.valueOf(node.getId())][i][j] = -2;
			}
		}

		for (int dstNode = 0; dstNode < nodesNumber; dstNode++) {
			if (dstNode == Integer.valueOf(node.getId())) { // deliver to me
				routingTables[Integer.valueOf(node.getId())][0][dstNode] = -1;
				continue;
			}

			// check out the dst Node's position first
			int dstRow = dstNode / gEdgeSize;
			int dstCol = dstNode % gEdgeSize;

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
					if (Integer.valueOf(getLinkTopologyParameter(links[Integer.valueOf(node.getLink().get(i).getValue())], TopologyParameter.ROW_TO)) == nextStepRow
							&& Integer.valueOf(getLinkTopologyParameter(links[Integer.valueOf(node.getLink().get(i).getValue())], TopologyParameter.COLUMN_TO)) == nextStepCol) {
						routingTables[Integer.valueOf(node.getId())][0][dstNode] = Integer.valueOf(links[Integer
								.valueOf(node.getLink().get(i).getValue())]
								.getId());
						break;
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
	
	/**
	 * Constructor
	 * 
	 * @param topologyId
	 *            the ID of the NoC topology used by this algorithm
	 * @param nodesNumber
	 *            the size of the 2D mesh (nodesNumber * nodesNumber)
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
	 */
	public BranchAndBoundMapper(String topologyId, int nodesNumber, int coresNumber, int linkBandwidth,
			int priorityQueueSize, float bufReadEBit, float bufWriteEBit) {
		this(topologyId, nodesNumber, coresNumber, linkBandwidth, priorityQueueSize, false,
				LegalTurnSet.WEST_FIRST, bufReadEBit, bufWriteEBit);
	}

	/**
	 * Constructor
	 * 
	 * @param topologyId
	 *            the ID of the NoC topology used by this algorithm
	 * @param nodesNumber
	 *            the size of the 2D mesh (nodesNumber * nodesNumber)
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
	 */
	public BranchAndBoundMapper(String topologyId, int nodesNumber, int coresNumber, int linkBandwidth,
			int priorityQueueSize, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit) {
		this.topologyId = topologyId;
		this.nodesNumber = nodesNumber;
		this.edgeSize = (int) Math.sqrt(nodesNumber);
		this.coresNumber = coresNumber;
		this.linkBandwidth = linkBandwidth;
		this.priorityQueueSize = priorityQueueSize;
		// we have 2gEdgeSize(edgeSize - 1) bidirectional links =>
		// 4gEdgeSize(edgeSize - 1) unidirectional links
		this.linksNumber = 2 * (edgeSize - 1) * edgeSize * 2;
		this.buildRoutingTable = buildRoutingTable;
		this.legalTurnSet = legalTurnSet;
		this.bufReadEBit = bufReadEBit;
		this.bufWriteEBit = bufWriteEBit;

		nodes = new NodeType[nodesNumber];

		cores = new Core[coresNumber];

		links = new LinkType[linksNumber];
	}

	public void initializeCores() {
		for (int i = 0; i < cores.length; i++) {
			cores[i] = new Core(i, -1);
			cores[i].setFromCommunication(new int[nodesNumber]);
			cores[i].setToCommunication(new int[nodesNumber]);
			cores[i].setFromBandwidthRequirement(new int[nodesNumber]);
			cores[i].setToBandwidthRequirement(new int[nodesNumber]);
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

	public void parseApcg(ApcgType apcg, CtgType ctg, int linkBandwidth) {
		logger.assertLog(apcg != null, "The APCG cannot be null");
		logger.assertLog(ctg != null, "The CTG cannot be null");
		
		List<CoreType> coreList = apcg.getCore();
		for (int i = 0; i < coreList.size(); i++) {
			CoreType coreType = coreList.get(i);
			List<TaskType> taskList = coreType.getTask();
			for (int j = 0; j < taskList.size(); j++) {
				TaskType taskType = taskList.get(j);
				String taskId = taskType.getId();
				List<CommunicationType> communications = getCommunications(ctg, taskId);
				for (int k = 0; k < communications.size(); k++) {
					CommunicationType communicationType = communications.get(k);
					cores[Integer
							.valueOf(communicationType.getSource().getId())]
							.getToCommunication()[Integer
							.valueOf(communicationType.getDestination().getId())] = (int) communicationType
							.getVolume();
					cores[Integer
							.valueOf(communicationType.getSource().getId())]
							.getToBandwidthRequirement()[Integer
							.valueOf(communicationType.getDestination().getId())] = (int) (3 * (communicationType
							.getVolume() / 1000000) * linkBandwidth);
					cores[Integer.valueOf(communicationType.getDestination()
							.getId())].getFromCommunication()[Integer
							.valueOf(communicationType.getSource().getId())] = (int) communicationType
							.getVolume();
					cores[Integer.valueOf(communicationType.getDestination()
							.getId())].getFromBandwidthRequirement()[Integer
							.valueOf(communicationType.getSource().getId())] = (int) (3 * (communicationType
							.getVolume() / 1000000) * linkBandwidth);
				}
			}
		}
	}

	public void initializeNocTopology(float switchEBit, float linkEBit) {
		// initialize nodes
		ObjectFactory nodeFactory = new ObjectFactory();
		for (int i = 0; i < nodes.length; i++) {
			NodeType node = nodeFactory.createNodeType();
			node.setId(Integer.toString(i));
			node.setCore(Integer.toString(-1));
			TopologyParameterType row = new TopologyParameterType();
			row.setTopology(topologyId);
			row.setType("row");
			row.setValue(Integer.toString(i / edgeSize));
			node.getTopologyParameter().add(row);
			TopologyParameterType column = new TopologyParameterType();
			column.setTopology(topologyId);
			column.setType("column");
			column.setValue(Integer.toString(i % edgeSize));
			node.getTopologyParameter().add(column);
			node.setCost((double)switchEBit);
			nodes[i] = node;
		}
		// initialize links
		for (int i = 0; i < links.length; i++) {
			// There are totally 2*(edgeSize-1)*edgeSize*2 links. The first
			// half links are horizontal
			// the second half links are vertical links.
			int fromNodeRow;
			int fromNodeColumn;
			int toNodeRow;
			int toNodeColumn;
			if (i < 2 * (edgeSize - 1) * edgeSize) {
				fromNodeRow = i / (2 * (edgeSize - 1));
				toNodeRow = i / (2 * (edgeSize - 1));
				int localId = i % (2 * (edgeSize - 1));
				if (localId < (edgeSize - 1)) {
					// from west to east
					fromNodeColumn = localId;
					toNodeColumn = localId + 1;
				} else {
					// from east to west
					localId = localId - (edgeSize - 1);
					fromNodeColumn = localId + 1;
					toNodeColumn = localId;
				}
			} else {
				int localId = i - 2 * (edgeSize - 1) * edgeSize;
				fromNodeColumn = localId / (2 * (edgeSize - 1));
				toNodeColumn = localId / (2 * (edgeSize - 1));
				localId = localId % (2 * (edgeSize - 1));
				if (localId < (edgeSize - 1)) {
					// from south to north
					fromNodeRow = localId;
					toNodeRow = localId + 1;
				} else {
					// from north to south
					localId = localId - (edgeSize - 1);
					fromNodeRow = localId + 1;
					toNodeRow = localId;
				}
			}

			int fromNodeId = fromNodeRow * edgeSize + fromNodeColumn;
			int toNodeId = toNodeRow * edgeSize + toNodeColumn;

			LinkType link = new LinkType();
			link.setId(Integer.toString(i));
			link.setBandwidth(linkBandwidth);
			link.setSourceNode(Integer.toString(fromNodeId));
			link.setDestinationNode(Integer.toString(toNodeId));
			link.setCost((double)linkEBit);
			ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType rowTo = new ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType();
			rowTo.setTopology(topologyId);
			rowTo.setType(TopologyParameter.ROW_TO.toString());
			rowTo.setValue(Integer.toString(toNodeRow));
			link.getTopologyParameter().add(rowTo);
			ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType rowFrom = new ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType();
			rowFrom.setTopology(topologyId);
			rowFrom.setType(TopologyParameter.ROW_FROM.toString());
			rowFrom.setValue(Integer.toString(fromNodeRow));
			link.getTopologyParameter().add(rowFrom);
			ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType columnTo = new ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType();
			columnTo.setTopology(topologyId);
			columnTo.setType(TopologyParameter.COLUMN_TO.toString());
			columnTo.setValue(Integer.toString(toNodeColumn));
			link.getTopologyParameter().add(columnTo);
			ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType columnFrom = new ro.ulbsibiu.acaps.noc.xml.link.TopologyParameterType();
			columnFrom.setTopology(topologyId);
			columnFrom.setType(TopologyParameter.COLUMN_FROM.toString());
			columnFrom.setValue(Integer.toString(fromNodeColumn));
			link.getTopologyParameter().add(columnFrom);
			links[i] = link;
		}
		// attach the links to the NoC nodes
		boolean inAdded = false;
		boolean outAdded = false;
		for (int i = 0; i < nodesNumber; i++) {
			for (int j = 0; j < links.length; j++) {
				if (Integer.valueOf(getLinkTopologyParameter(links[j], TopologyParameter.ROW_FROM)).intValue() == Integer.valueOf(getNodeTopologyParameter(nodes[i], TopologyParameter.ROW)).intValue()
						&& Integer.valueOf(getLinkTopologyParameter(links[j], TopologyParameter.COLUMN_FROM)).intValue() == Integer.valueOf(getNodeTopologyParameter(nodes[i], TopologyParameter.COLUMN)).intValue()) {
					ro.ulbsibiu.acaps.noc.xml.node.LinkType linkType = new ro.ulbsibiu.acaps.noc.xml.node.LinkType();
					linkType.setType(LINK_OUT);
					linkType.setValue(links[j].getId());
					nodes[i].getLink().add(linkType);
					outAdded = true;
				}
				if (Integer.valueOf(getLinkTopologyParameter(links[j], TopologyParameter.ROW_TO)).intValue() == Integer.valueOf(getNodeTopologyParameter(nodes[i], TopologyParameter.ROW)).intValue()
						&& Integer.valueOf(getLinkTopologyParameter(links[j], TopologyParameter.COLUMN_TO)).intValue() == Integer.valueOf(getNodeTopologyParameter(nodes[i], TopologyParameter.COLUMN)).intValue()) {
					ro.ulbsibiu.acaps.noc.xml.node.LinkType linkType = new ro.ulbsibiu.acaps.noc.xml.node.LinkType();
					linkType.setType(LINK_IN);
					linkType.setValue(links[j].getId());
					nodes[i].getLink().add(linkType);
					inAdded = true;
				}
			}
			logger.assertLog(inAdded, null);
			logger.assertLog(outAdded, null);
		}
		// for each router generate a routing table provided by the XY routing
		// protocol
		routingTables = new int[nodesNumber][nodesNumber][nodesNumber];
		for (int i = 0; i < nodesNumber; i++) {
			generateXYRoutingTable(nodes[i], nodesNumber, edgeSize, links);
		}

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
						currentNode = nodes[Integer.valueOf(link.getDestinationNode())];
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
		Random rand = new Random();
		for (int i = 0; i < coresNumber; i++) {
			int k = Math.abs(rand.nextInt()) % nodesNumber;
			while (!Integer.toString(-1).equals(nodes[k].getCore())) {
				k = Math.abs(rand.nextInt()) % nodesNumber;
			}
			cores[i].setNodeId(k);
			nodes[k].setCore(Integer.toString(i));
		}

		// // this maps the cores like NoCMap does
		// int[] coreMap = new int[] { 11, 13, 10, 8, 12, 0, 9, 1, 2, 4, 14, 15,
		// 5, 3, 7, 6 };
		// for (int i = 0; i < coresNumber; i++) {
		// cores[i].setNodeId(coreMap[i]);
		// nodes[coreMap[i]].setProcId(i);
		// }
	}

	private void printCurrentMapping() {
		for (int i = 0; i < coresNumber; i++) {
			System.out.println("Core " + cores[i].getCoreId()
					+ " is mapped to NoC node " + cores[i].getNodeId());
		}
	}

	/**
	 * Initialization function for Branch-and-Bound mapping
	 */
	private void init() {
		if (logger.isInfoEnabled()) {
			logger.info("Initialize for branch-and-bound");
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
		procMatrix = new int[coresNumber][coresNumber];
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
					currentNode = nodes[Integer.valueOf(link.getDestinationNode())];
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
			int max = -1;
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
		System.out.println("Totally " + MappingNode.cnt
				+ " have been generated");
		if (bestMapping != null) {
			applyMapping(bestMapping);
		} else {
			System.out.println("Can not find a suitable solution.");
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
						System.out
								.println("Current minimum cost upper bound is "
										+ minUpperBound);
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
						System.out
								.println("Current minimum cost is " + minCost);
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
				System.out.println("Current minimum cost is " + minCost);
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
				System.out.println("Current minimum cost upper bound is "
						+ minUpperBound);
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
					System.out.println("Current minimum cost is " + minCost);
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
			ObjectFactory nodeFactory = new ObjectFactory();
			for (int i = 0; i < nodes.length; i++) {
				StringWriter stringWriter = new StringWriter();
				JAXBElement<NodeType> node = nodeFactory.createNode(nodes[i]);
				marshaller.marshal(node, stringWriter);	
				PrintWriter pw = new PrintWriter("src"
						+ File.separator
						+ this.getClass().getPackage().getName()
								.replace(".", File.separator) + File.separator
						+ "output" + File.separator + topologyId
						+ File.separator + "nodes" + File.separator + "node-"
						+ i + ".xml");
				logger.info("Saving the XML for node " + i);
				pw.write(stringWriter.toString());
				pw.close();
			}
			// save the links
			jaxbContext = JAXBContext.newInstance(LinkType.class);
			marshaller = jaxbContext.createMarshaller();
			ro.ulbsibiu.acaps.noc.xml.link.ObjectFactory linkFactory = new ro.ulbsibiu.acaps.noc.xml.link.ObjectFactory();
			for (int i = 0; i < links.length; i++) {
				StringWriter stringWriter = new StringWriter();
				JAXBElement<LinkType> link = linkFactory.createLink(links[i]);
				marshaller.marshal(link, stringWriter);	
				PrintWriter pw = new PrintWriter("src"
						+ File.separator
						+ this.getClass().getPackage().getName()
								.replace(".", File.separator) + File.separator
						+ "output" + File.separator + topologyId
						+ File.separator + "links" + File.separator + "link-"
						+ i + ".xml");
				logger.info("Saving the XML for link " + i);
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

		printCurrentMapping();

		long start = System.currentTimeMillis();
		System.out.println("Start mapping...");

		logger.assertLog((coresNumber == ((int) cores.length)), null);

		branchAndBoundMapping();
		long end = System.currentTimeMillis();
		System.out.println("Mapping process finished successfully.");
		System.out.println("Time: " + (end - start) / 1000 + " seconds");

		saveRoutingTables();
		
		saveTopology();

		MappingType mapping = new MappingType();
		mapping.setId("0");
		mapping.setApcg("0");
		for (int i = 0; i < nodes.length; i++) {
			MapType map = new MapType();
			map.setNode(nodes[i].getId());
			map.setCore(nodes[i].getCore());
			mapping.getMap().add(map);
		}
		StringWriter stringWriter = new StringWriter();
		ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory mappingFactory = new ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory();
		JAXBElement<MappingType> jaxbElement = mappingFactory.createMapping(mapping);
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(MappingType.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.marshal(jaxbElement, stringWriter);
		} catch (JAXBException e) {
			logger.error("JAXB encountered an error", e);
		}

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
			
			int[] toCommunication = cores[i].getToCommunication();
			System.out.println("to communication");
			for (int j = 0; j < toCommunication.length; j++) {
				System.out.println(toCommunication[j]);
			}
			
			int[] fromCommunication = cores[i].getFromCommunication();
			System.out.println("from communication");
			for (int j = 0; j < fromCommunication.length; j++) {
				System.out.println(fromCommunication[j]);
			}
			
			int[] toBandwidthRequirement = cores[i].getToBandwidthRequirement();
			System.out.println("to bandwidth requirement");
			for (int j = 0; j < toBandwidthRequirement.length; j++) {
				System.out.println(toBandwidthRequirement[j]);
			}
			
			int[] fromBandwidthRequirement = cores[i].getFromBandwidthRequirement();
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
		            int commLoad = cores[srcProc].getToBandwidthRequirement()[dstProc];
		            if (commLoad == 0) {
		                continue;
		            }
		            NodeType currentNode = nodes[src];
		            while (Integer.valueOf(currentNode.getId()) != dst) {
		                int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
		                LinkType link = links[linkId];
		                currentNode = nodes[Integer.valueOf(link.getDestinationNode())];
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
					int commVol = cores[srcProc].getToCommunication()[dstProc];
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
							currentNode = nodes[Integer.valueOf(links[linkId].getDestinationNode())];
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
					int commVol = cores[srcProc].getToCommunication()[dstProc];
					if (commVol > 0) {
						NodeType currentNode = nodes[src];
						while (Integer.valueOf(currentNode.getId()) != dst) {
							int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
							energy += links[linkId].getCost() * commVol;
							currentNode = nodes[Integer.valueOf(links[linkId].getDestinationNode())];
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
					int commVol = cores[srcProc].getToCommunication()[dstProc];
					if (commVol > 0) {
						NodeType currentNode = nodes[src];
						while (Integer.valueOf(currentNode.getId()) != dst) {
							int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
							energy += (bufReadEBit + bufWriteEBit) * commVol;
							currentNode = nodes[Integer.valueOf(links[linkId].getDestinationNode())];
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
	    System.out.print("Verify the communication load of each link...");
	    if (verifyBandwidthRequirement()) {
	    	System.out.println("Succeed.");
	    }
	    else {
	    	System.out.println("Fail.");
	    }
	    System.out.println("Energy consumption estimation ");
	    System.out.println("(note that this is not exact numbers, but serve as a relative energy indication) ");
	    System.out.println("Energy consumed in link is " + calculateLinkEnergy());
	    System.out.println("Energy consumed in switch is " + calculateSwitchEnergy());
	    System.out.println("Energy consumed in buffer is " + calculateBufferEnergy());
	    System.out.println("Total communication energy consumption is " + calculateCommunicationEnergy());
	}
	
	public static void main(String[] args) throws TooFewNocNodesException,
			IOException, JAXBException {
		if (args == null || args.length < 1) {
			System.err.println("usage: BranchAndBoundMapper {routing}");
			System.err
					.println("(where routing may be true or false; any other value means false)");
		} else {
			// the mesh-2D.xml from NoC-XML
			JAXBContext jaxbContext = JAXBContext
					.newInstance("ro.ulbsibiu.acaps.noc.xml.topologyParameter");
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			@SuppressWarnings("unchecked")
			TopologyType topology = ((JAXBElement<TopologyType>) unmarshaller
					.unmarshal(new File(".." + File.separator + "NoC-XML"
							+ File.separator + "src" + File.separator + "ro"
							+ File.separator + "ulbsibiu" + File.separator
							+ "acaps" + File.separator + "noc" + File.separator
							+ "topology" + File.separator + "mesh2D"
							+ File.separator + "mesh-2D" + ".xml"))).getValue();
			String topologyId = topology.getId();
			// from the initial random mapping, I think tiles must equal cores
			// (it
			// is not enough to have cores <= tiles)
			int tiles = 16;
			int cores = 9;
			int linkBandwidth = 1000000;
			int priorityQueueSize = 2000;
			float switchEBit = 0.284f;
			float linkEBit = 0.449f;
			float bufReadEBit = 1.056f;
			float bufWriteEBit = 2.831f;

			BranchAndBoundMapper bbMapper;
			if ("true".equals(args[0])) {
				// Branch and Bound with routing
				bbMapper = new BranchAndBoundMapper(topologyId, tiles, cores,
						linkBandwidth, priorityQueueSize, true,
						LegalTurnSet.ODD_EVEN, bufReadEBit, bufWriteEBit);
			} else {
				// Branch and Bound without routing
				bbMapper = new BranchAndBoundMapper(topologyId, tiles, cores,
						linkBandwidth, priorityQueueSize, bufReadEBit, bufWriteEBit);
			}

			jaxbContext = JAXBContext
					.newInstance("ro.ulbsibiu.acaps.ctg.xml.apcg");
			unmarshaller = jaxbContext.createUnmarshaller();
			@SuppressWarnings("unchecked")
			ApcgType apcg = ((JAXBElement<ApcgType>) unmarshaller
					.unmarshal(new File("src"
							+ File.separator
							+ BranchAndBoundMapper.class.getPackage()
									.getName().replace(".", File.separator)
							+ File.separator + "input" + File.separator
							+ "auto-indust-mocsyn.tgff"
							+ File.separator + "apcg-2" + ".xml"))).getValue();

			jaxbContext = JAXBContext
					.newInstance("ro.ulbsibiu.acaps.ctg.xml.ctg");
			unmarshaller = jaxbContext.createUnmarshaller();
			@SuppressWarnings("unchecked")
			CtgType ctg = ((JAXBElement<CtgType>) unmarshaller
					.unmarshal(new File("src"
							+ File.separator
							+ BranchAndBoundMapper.class.getPackage()
									.getName().replace(".", File.separator)
							+ File.separator + "input" + File.separator
							+ "auto-indust-mocsyn.tgff"
							+ File.separator + "ctg-2" + ".xml"))).getValue();

			bbMapper.initializeCores();
			bbMapper.initializeNocTopology(switchEBit, linkEBit);

//			// read the input data from a traffic.config file (NoCmap style)
//			bbMapper.parseTrafficConfig(
//					"telecom-mocsyn-16tile-selectedpe.traffic.config",
//					linkBandwidth);
			
			// read the input data using the Unified Framework's XML interface
			bbMapper.parseApcg(apcg, ctg, linkBandwidth);
			
//			// This is just for checking that bbMapper.parseTrafficConfig(...)
//			// and parseApcg(...) have the same effect
//			bbMapper.printCores();

			String mappingXml = bbMapper.map();
			PrintWriter pw = new PrintWriter("src"
					+ File.separator
					+ BranchAndBoundMapper.class.getPackage().getName()
							.replace(".", File.separator) + File.separator
					+ "output" + File.separator + topologyId + File.separator
					+ "mapping" + ".xml");
			logger.info("Saving the mapping XML file");
			pw.write(mappingXml);
			pw.close();

			bbMapper.printCurrentMapping();
			
			bbMapper.analyzeIt();
		}
	}

}
