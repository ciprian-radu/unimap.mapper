package ro.ulbsibiu.acaps.mapper.sa;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.noc.xml.link.LinkType;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;
import ro.ulbsibiu.acaps.noc.xml.node.ObjectFactory;
import ro.ulbsibiu.acaps.noc.xml.node.RoutingTableEntryType;
import ro.ulbsibiu.acaps.noc.xml.node.TopologyParameterType;

/**
 * Simulated Annealing algorithm for Network-on-Chip (NoC) application mapping.
 * The implementation is based on the one from <a
 * href="http://www.ece.cmu.edu/~sld/wiki/doku.php?id=shared:nocmap">NoCMap</a>
 * 
 * <p>
 * Note that currently, this algorithm works only with N x N 2D mesh NoCs
 * </p>
 * 
 * @author cipi
 * 
 */
public class SimulatedAnnealingMapper implements Mapper {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(SimulatedAnnealingMapper.class);

	private static final int NORTH = 0;

	private static final int SOUTH = 1;

	private static final int EAST = 2;

	private static final int WEST = 3;

	/** tolerance for the cost function of the algorithm */
	private static final int TOLERANCE = 1;

	/** how many temperature variations the algorithm should try */
	private static final int TEMPS = 5;

	/**
	 * the minimum acceptance ratio (number of viable IP core mappings vs. the
	 * total number of tried mappings)
	 */
	private static final double MINACCEPT = 0.001;

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

	/**
	 * the number of links from the NoC
	 */
	private int linksNumber;

	/** the nodes from the Network-on-Chip (NoC) */
	private NodeType[] nodes;

	/** the processes (tasks, cores) */
	private Core[] cores;

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
	private int attempts;

	/** how many zero cost mappings where accepted */
	private int zeroCostAcceptance;

	/**
	 * specifies if the algorithm must be stopped "manually". This is typically
	 * done when the zero cost acceptance is 10
	 */
	private boolean needStop;

	/** the cost of the current mapping */
	private double currentCost;

	/** the acceptance ratio */
	private double acceptRatio;

	/** how many zero cost mappings are currently accepted */
	private int zeroTempCnt = 0;

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

	/** the ID of the NoC topology used by this algorithm */
	private String topologyId;
	
	private static enum TopologyParameter {
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
	
	private String getNodeTopologyParameter(NodeType node,
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
	private int[][][] routingTables;
	
	public void generateXYRoutingTable(NodeType node, int nodesNumber, int gEdgeSize, LinkType[] links) {
		for (int i = 0; i < nodesNumber; i++) {
			for (int j = 0; j < nodesNumber; j++) {
				RoutingTableEntryType routingTableEntryType = new RoutingTableEntryType();
				routingTableEntryType.setSource(Integer.toString(i));
				routingTableEntryType.setDestination(Integer.toString(j));
				routingTableEntryType.setLink(Integer.toString(-2));
				node.getRoutingTableEntry().add(routingTableEntryType);
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
	 * Default constructor
	 * <p>
	 * No routing table is built.
	 * </p>
	 * 
	 * @param topologyId
	 *            the ID of the NoC topology used by this algorithm
	 * @param nodesNumber
	 *            the size of the 2D mesh (nodesNumber * nodesNumber)
	 * @param coresNumber
	 *            the number of processes (tasks). Note that each core has only
	 *            one task associated to it
	 */
	public SimulatedAnnealingMapper(String topologyId, int nodesNumber, int coresNumber) {
		this(topologyId, nodesNumber, coresNumber, false, LegalTurnSet.WEST_FIRST, 1.056f, 2.831f);
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
	public SimulatedAnnealingMapper(String topologyId, int nodesNumber, int coresNumber,
			boolean buildRoutingTable, LegalTurnSet legalTurnSet,
			float bufReadEBit, float bufWriteEBit) {
		this.topologyId = topologyId;
		this.nodesNumber = nodesNumber;
		this.edgeSize = (int) Math.sqrt(nodesNumber);
		this.coresNumber = coresNumber;
		// we have 2gEdgeSize(gEdgeSize - 1) bidirectional links =>
		// 4gEdgeSize(gEdgeSize - 1) unidirectional links
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

	public void initializeNocTopology(int bandwidth, float switchEBit,
			float linkEBit) {
		// initialize nodes
		ObjectFactory nodeFactory = new ObjectFactory();
		for (int i = 0; i < nodes.length; i++) {
			NodeType node = nodeFactory.createNodeType();
			node.setId(Integer.toString(i));
			node.setCore(Integer.toString(-1));
			TopologyParameterType row = new TopologyParameterType();
			row.setTopology(topologyId);
			row.setType(TopologyParameter.ROW.toString());
			row.setValue(Integer.toString(i / edgeSize));
			node.getTopologyParameter().add(row);
			TopologyParameterType column = new TopologyParameterType();
			column.setTopology(topologyId);
			column.setType(TopologyParameter.COLUMN.toString());
			column.setValue(Integer.toString(i % edgeSize));
			node.getTopologyParameter().add(column);
			node.setCost((double)switchEBit);
			nodes[i] = node;
		}
		// initialize links
		for (int i = 0; i < links.length; i++) {
			// There are totally 2*(gEdgeSize-1)*gEdgeSize*2 links. The first
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
			link.setBandwidth(bandwidth);
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
				if (Integer.valueOf(getLinkTopologyParameter(links[j], TopologyParameter.ROW_FROM)) == Integer.valueOf(getNodeTopologyParameter(nodes[i], TopologyParameter.ROW))
						&& Integer.valueOf(getLinkTopologyParameter(links[j], TopologyParameter.COLUMN_FROM)) == Integer.valueOf(getNodeTopologyParameter(nodes[i], TopologyParameter.COLUMN))) {
					ro.ulbsibiu.acaps.noc.xml.node.LinkType linkType = new ro.ulbsibiu.acaps.noc.xml.node.LinkType();
					linkType.setType(LINK_OUT);
					linkType.setValue(links[j].getId());
					nodes[i].getLink().add(linkType);
					outAdded = true;
				}
				if (Integer.valueOf(getLinkTopologyParameter(links[j], TopologyParameter.ROW_TO)) == Integer.valueOf(getNodeTopologyParameter(nodes[i], TopologyParameter.ROW))
						&& Integer.valueOf(getLinkTopologyParameter(links[j], TopologyParameter.COLUMN_TO)) == Integer.valueOf(getNodeTopologyParameter(nodes[i], TopologyParameter.COLUMN))) {
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
		for (int i = 0; i < nodesNumber; i++) {
			int k = Math.abs(rand.nextInt()) % nodesNumber;
			while (Integer.valueOf(nodes[k].getCore()) != -1) {
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
		// gNode[coreMap[i]].setProcId(i);
		// }
	}

	private void printCurrentMapping() {
		for (int i = 0; i < coresNumber; i++) {
			System.out.println("Core " + cores[i].getCoreId()
					+ " is mapped to NoC node " + cores[i].getNodeId());
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
	 * the usual metropolis accept criterion
	 * 
	 * @param deltac
	 *            the cost (energy) variation
	 * @param temperature
	 *            the temperature
	 * 
	 * @return <tt>true</tt> for accept, <tt>false</tt>, otherwise
	 */
	private boolean accept(double deltac, double temperature) {
		double pa = -1; // probability of acceptance
		boolean accept = false;
		double r = -1;
		// annealing accept criterion
		if (MathUtils.approximatelyEqual((float) deltac, 0)) {
			// accept it, but record the number of zero cost acceptance
			zeroCostAcceptance++;
		}

		if (MathUtils.definitelyLessThan((float) deltac, 0)
				|| MathUtils.approximatelyEqual((float) deltac, 0)) {
			accept = true;
		} else {
			pa = Math.exp((double) (-deltac) / temperature);
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
	private double annealAtTemperature(double t) {
		int acceptCount = 0;
		double totalDeltaCost = 0;

		int unit = attempts / 10;

		// clear the zeroCostAcceptance
		zeroCostAcceptance = 0;

		// this is the main loop doing moves. We do 'attempts' moves in all,
		// then quit at this temperature

		if (logger.isTraceEnabled()) {
			logger.trace("attempts = " + attempts);
		}
		for (int m = 1; m < attempts; m++) {
			int[] nodes = makeRandomSwap();
			int node1 = nodes[0];
			int node2 = nodes[1];
			double newCost = calculateTotalCost();
			double deltaCost = newCost - currentCost;
			if (logger.isDebugEnabled()) {
				logger.debug("deltaCost " + deltaCost + " newCost " + newCost
						+ " currentCost " + currentCost);
			}
	        double deltac = deltaCost / currentCost;
			// Note that we use machine epsilon to perform the following
			// comparison between the float numbers
	        if (MathUtils.approximatelyEqual((float)deltac, 0)) {
	            deltac = 0;
	        } else {
	            deltac = deltac * 100;
	        }
			if (accept(deltac, t)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Accepting...");
				}
				acceptCount++;
				totalDeltaCost += deltaCost;
				currentCost = newCost;
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("Rolling back nodes " + node1 + " and " + node2);
				}
				swapProcesses(node1, node2); // roll back
			}
			if (m % unit == 0) {
				// This is just to print out the process of the algorithm
				System.out.print("#");
			}
		}
		System.out.println();
		acceptRatio = ((double) acceptCount) / attempts;

		if (zeroCostAcceptance == acceptCount) {
			zeroTempCnt++;
		}
		else {
			zeroTempCnt = 0;
		}

		if (zeroTempCnt == 10) {
			needStop = true;
		}

		return totalDeltaCost;
	}

	private void anneal() {
		double cost3, cost2;
		boolean done;
		double tol3, tol2, temp;
		double deltaCost;

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
		int tempCount = 0;
		cost3 = 999999999;
		cost2 = 999999999;
		currentCost = cost2;

		attempts = nodesNumber * nodesNumber * 100;
		// attempts = nodesNumber * 10;

		initRand(1234567);

		// Determin initial temperature by accepting all moves and
		// calculate variance.
		/*
		 * compute initial temperature anneal_at_temp(10000.0, costcurrent,
		 * &acceptratio, 1); temp = 20.0 * VAR; init_anneal();
		 */

		temp = 100;

		/* here is the temperature cooling loop of the annealer */
		done = false;
		do {
			needStop = false;

			System.out.println("Round " + tempCount + ":");
			System.out.println("Current Annealing temperature " + temp);

			deltaCost = annealAtTemperature(temp);
			
//			System.exit(-1);

			System.out.println("total delta cost " + deltaCost);
			System.out.println("Current cost " + currentCost);
			System.out.println("Accept ratio " + acceptRatio);
			
//			printCurrentMapping();

			// OK, if we got here the cost function is working fine. We can
			// now look at whether we are frozen, or whether we should cool some
			// more. We basically just look at the last 2 temperatures, and
			// see if the cost is not changing much (that's the TOLERANCE test)
			// and if the we have done enough temperatures (that's the TEMPS
			// test), and if the accept ratio fraction is small enough (that is
			// the MINACCEPT test). If all are satisfied, we quit.

			tol3 = ((double) cost3 - (double) cost2) / (double) cost3;
			if (tol3 < 0) {
				tol3 = -tol3;
			}
			tol2 = ((double) cost2 - (double) currentCost) / (double) cost2;
			if (tol2 < 0) {
				tol2 = -tol2;
			}

			if (MathUtils.definitelyLessThan((float) tol3, TOLERANCE)
					&& MathUtils.definitelyLessThan((float) tol3, TOLERANCE)
					&& tempCount > TEMPS
					&& (MathUtils.definitelyLessThan((float) acceptRatio,
							(float) MINACCEPT) || needStop)) {
				done = true;
			} else {
				// save the relevant info to test for frozen after the NEXT
				// temperature.
				cost3 = cost2;
				cost2 = currentCost;
				temp = 0.9 * temp;
				tempCount++;
			}
		} while (!done);
		if (buildRoutingTable) {
			programRouters();
		}
	}

	/**
	 * Randomly picks two nodes and swaps them
	 * 
	 * @return an array with exactly 2 integers
	 */
	private int[] makeRandomSwap() {
		int node1 = (int) uniformIntegerRandomVariable(0, nodesNumber - 1);
		int node2 = -1;

		while (true) {
			// select two nodes to swap
			node2 = (int) uniformIntegerRandomVariable(0, nodesNumber - 1);
			if (node1 != node2
					&& (nodes[node1] != null || nodes[node2] != null)) {
				break;
			}
		}

		// Swap the processes attached to these two nodes
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
		
		if (logger.isTraceEnabled()) {
			logger.trace("Swapping process " + p1 + " of node " + t1
					+ " with process " + p2 + " of node " + t2);
		}
		
		node1.setCore(Integer.toString(p2));
		node2.setCore(Integer.toString(p1));
		if (p1 != -1) {
			Core process = cores[p1];
			if (process == null) {
				process = new Core(p1, t2);
			} else {
				process.setNodeId(t2);
			}
		}
		if (p2 != -1) {
			Core process = cores[p2];
			if (process == null) {
				process = new Core(p2, t1);
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
						/ links[i].getBandwidth() - 1.0f;
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
	private void routeTraffic(int srcNode, int dstNode, int bandwidth) {
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
				int commVol = cores[srcProc].getToCommunication()[dstProc];
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
						currentNode = nodes[Integer.valueOf(links[linkId].getDestinationNode())];
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
		return energy;
	}

	private float calculateLinkEnergy() {
		float energy = 0;
		for (int src = 0; src < nodesNumber; src++) {
			for (int dst = 0; dst < nodesNumber; dst++) {
				int srcProc = Integer.valueOf(nodes[src].getCore());
				int dstProc = Integer.valueOf(nodes[dst].getCore());
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
		return energy;
	}

	private float calculateBufferEnergy() {
		float energy = 0;
		for (int src = 0; src < nodesNumber; src++) {
			for (int dst = 0; dst < nodesNumber; dst++) {
				int srcProc = Integer.valueOf(nodes[src].getCore());
				int dstProc = Integer.valueOf(nodes[dst].getCore());
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
			if (Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getSourceNode())], TopologyParameter.ROW)) == origRow
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getSourceNode())], TopologyParameter.COLUMN)) == origColumn
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getDestinationNode())], TopologyParameter.ROW)) == row
					&& Integer.valueOf(getNodeTopologyParameter(nodes[Integer.valueOf(links[linkId].getDestinationNode())], TopologyParameter.COLUMN)) == column)
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
	
	private void saveRoutingTables() {
		if (logger.isInfoEnabled()) {
			logger.info("Saving the routing tables");
		}
		
		for (int i = 0; i < nodes.length; i++) {
			int[][] routingEntries = routingTables[Integer.valueOf(nodes[i].getId())];
			for (int j = 0; j < routingEntries.length; j++) {
				for (int k = 0; k < routingEntries[j].length; k++) {
					if (routingEntries[i][j] >= 0) {
						RoutingTableEntryType routingTableEntry = new RoutingTableEntryType();
						routingTableEntry.setSource(Integer.toString(i));
						routingTableEntry.setDestination(Integer.toString(j));
						routingTableEntry.setLink(Integer.toString(routingEntries[i][j]));
						nodes[i].getRoutingTableEntry().add(routingTableEntry);
					}
				}
			}
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

		anneal();
		long end = System.currentTimeMillis();
		System.out.println("Mapping process finished successfully.");
		System.out.println("Time: " + (end - start) / 1000 + " seconds");

		saveRoutingTables();
		
		// FIXME save the mapping and the routing tables into the XML
		return null;
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
			IOException {
		if (args == null || args.length < 1) {
			System.err.println("usage: SimulatedAnnealingMapper {routing}");
			System.err.println("(where routing may be true or false; any other value means false)");
		} else {
			// the mesh-2D.xml from NoC-XML
			String topologyId = "mesh2D";
			// from the initial random mapping, I think nodes must equal cores (it
			// is not enough to have cores <= nodes)
			int nodes = 16;
			int cores = 16;
			int linkBandwidth = 1000000;
			float switchEBit = 0.284f;
			float linkEBit = 0.449f;
			float bufReadEBit = 1.056f;
			float burWriteEBit = 2.831f;
	
			SimulatedAnnealingMapper saMapper;
			if ("true".equals(args[0])) {
				// SA with routing
				saMapper = new SimulatedAnnealingMapper(topologyId, nodes, cores, true,
						LegalTurnSet.ODD_EVEN, bufReadEBit, burWriteEBit);
			} else {
				// SA without routing
				saMapper = new SimulatedAnnealingMapper(topologyId, nodes, cores);
			}
			saMapper.initializeCores();
			saMapper.initializeNocTopology(linkBandwidth, switchEBit, linkEBit);
			
	
			saMapper.parseTrafficConfig(
					"telecom-mocsyn-16tile-selectedpe.traffic.config",
					linkBandwidth);
	
			saMapper.map();
	
			saMapper.printCurrentMapping();
			
			saMapper.analyzeIt();
			
			// FIXME save the routingTables into the NodeTypes
		}
	}
}
