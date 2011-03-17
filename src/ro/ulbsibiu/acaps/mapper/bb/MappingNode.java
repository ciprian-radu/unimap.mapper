package ro.ulbsibiu.acaps.mapper.bb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import static ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper.TopologyParameter.ROW;
import static ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper.TopologyParameter.COLUMN;

/**
 * Represents a node from the search tree of the Branch-and-Bound algorithm.
 * 
 * @see BranchAndBoundMapper
 * 
 * @author cipi
 * 
 */
class MappingNode {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(MappingNode.class);

	/**
	 * The routing can be made either "easy" or "hard".
	 * 
	 * @author cipi
	 * 
	 */
	public enum RoutingEffort {
		/**
		 * routes the traffic using odd even or west first routing
		 */
		EASY,
		/**
		 * routes the traffic by considering all available paths and selecting
		 * one with the minimal maximal bandwidth usage
		 */
		HARD
	}
	
	/** the Branch-and-Bound mapper */
	private BranchAndBoundMapper bbMapper;
	
	/** the unique identifier of this node */
	private int id;

	/** It is an illegal node if it violates the spec constructor will init this */
	private boolean illegal;

	/** counter (it is basically the ID of the mapping node) */
	static int cnt;

	/** How many processes have been mapped */
	private int stage;

	/**
	 * the current mapping of cores to nodes (mapping[i] = -1 value means no
	 * core is mapped to node i)
	 */
	private int[] mapping;

	/** specifies if a NoC node is occupied */
	private boolean[] tileOccupancyTable;

	/** the mapping cost */
	float cost;

	/** the lower bound */
	float lowerBound;

	/** the upper bound */
	float upperBound;

	/** specifies if the {@link #tileOccupancyTable} is ready to be used */
	private boolean occupancyTableReady;

	private boolean illegalChildMapping;

	/**
	 * records how much bandwidth has been used for each link (used for XY
	 * routing)
	 */
	private int[] linkBandwidthUsage;

	/**
	 * this array is used when we also need to synthesize the routing table.
	 * This can be indexed by [src_row][src_col][direction].
	 */
	private int[][][] rSynLinkBandwidthUsage;

	/** a temporary {@link #rSynLinkBandwidthUsage} */
	private int[][][] rSynLinkBandwidthUsageTemp;

	/** [row][col][src_tile][dst_tile] */
	private int[][][][] routingTable;

	/** 0: route in X; 1: route in Y */
	private int[] routingBitArray;

	/** The <tt>routingBitArray</tt> in integer form */
	private int routingInt;

	private int[] bestRoutingBitArray;

	private boolean firstRoutingPath;

	private int maxRoutingInt;

	/** the {@link MappingNode} that follows this node (in the search tree) */
	MappingNode next;

	/**
	 * Constructor
	 * 
	 * @param bbMapper
	 *            the {@link BranchAndBoundMapper} using this mapping node
	 *            (cannot be <tt>null</tt>)
	 * @param parent
	 *            the parent node
	 * @param tileId
	 *            the ID of the tile to which this node is attached to
	 * @param calcBound
	 *            whether or not to compute upper and lower cost bounds
	 */
	public MappingNode(final BranchAndBoundMapper bbMapper,
			final MappingNode parent, int tileId, boolean calcBound) {
		logger.assertLog(bbMapper != null,
				"The mapping node must be associated to a BranchAndBoundMapper");
		this.bbMapper = bbMapper;
		id = cnt;
		if (logger.isDebugEnabled()) {
			logger.debug("Creating mapping node with ID " + id
					+ ", having parent " + parent.id + " (tileId " + tileId
					+ ")");
		}

		illegal = false;

		tileOccupancyTable = null;
		mapping = null;
		linkBandwidthUsage = null;
		rSynLinkBandwidthUsage = null;
		rSynLinkBandwidthUsageTemp = null;
		
		if (bbMapper.buildRoutingTable) {
			routingTable = new int[bbMapper.hSize][bbMapper.nodes.length / bbMapper.hSize][bbMapper.nodes.length][bbMapper.nodes.length];
			for (int i = 0; i < routingTable.length; i++) {
				for (int j = 0; j < routingTable[i].length; j++) {
					for (int k = 0; k < routingTable[i][j].length; k++) {
						for (int l = 0; l < routingTable[i][j][k].length; l++) {
							routingTable[i][j][k][l] = -2;
						}
					}
				}
			}
		}

		routingBitArray = null;
		bestRoutingBitArray = null;

		occupancyTableReady = false;
		lowerBound = -1;
		cnt++;
		mapping = new int[bbMapper.cores.length];
		for (int i = 0; i < bbMapper.cores.length; i++) {
			mapping[i] = -1;
		}

		stage = parent.stage;

		int proc1 = bbMapper.procMapArray[stage];
		// if (bbMapper.cores[proc1]->is_locked() &&
		// bbMapper.cores[proc1]->lock_to !=
		// tileId) {
		// illegal = true;
		// return;
		// }

		lowerBound = parent.lowerBound;
		upperBound = parent.upperBound;

		// Copy the parent's partial mapping
		mapping = Arrays.copyOf(parent.mapping, bbMapper.cores.length);

		if (bbMapper.buildRoutingTable) {
			// Copy the parent's link bandwidth usage
			rSynLinkBandwidthUsage = new int[bbMapper.hSize][bbMapper.nodes.length / bbMapper.hSize][4];
			for (int i = 0; i < bbMapper.hSize; i++) {
				for (int j = 0; j < bbMapper.nodes.length / bbMapper.hSize; j++) {
					rSynLinkBandwidthUsage[i][j] = Arrays.copyOf(
							parent.rSynLinkBandwidthUsage[i][j], 4);
				}
			}
		} else {
			linkBandwidthUsage = new int[bbMapper.links.length];
			linkBandwidthUsage = Arrays.copyOf(parent.linkBandwidthUsage,
					bbMapper.links.length);
		}

		// Map the next process to tile tileId
		mapping[stage] = tileId;
		next = null;
		cost = parent.cost;

		for (int i = 0; i < stage; i++) {
			int tile1 = tileId;
			int tile2 = mapping[i];
			float thisTranCost = bbMapper.procMatrix[i][stage];
			thisTranCost = thisTranCost * bbMapper.archMatrix[tile1][tile2];
			cost += thisTranCost;
			if (MathUtils.definitelyGreaterThan(thisTranCost,
					BranchAndBoundMapper.MAX_PER_TRAN_COST)) {
				illegal = true;
				return;
			}
		}

		if (bbMapper.buildRoutingTable) {
			if (!routeTraffics(stage, stage, true, true)) {
				cost = BranchAndBoundMapper.MAX_VALUE + 1;
				illegal = true;
				return;
			}
		} else {
			for (int i = 0; i < stage; i++) {
				int tile1 = tileId;
				int tile2 = mapping[i];
				proc1 = bbMapper.procMapArray[stage];
				int proc2 = bbMapper.procMapArray[i];
				if (bbMapper.cores[proc1].getToBandwidthRequirement()[proc2] > 0) {
					for (int j = 0; j < bbMapper.linkUsageList[tile1][tile2]
							.size(); j++) {
						int linkId = bbMapper.linkUsageList[tile1][tile2]
								.get(j);
						linkBandwidthUsage[linkId] += bbMapper.cores[proc1]
								.getToBandwidthRequirement()[proc2];
						if (linkBandwidthUsage[linkId] > bbMapper.links[linkId]
								.getBandwidth()) {
							cost = BranchAndBoundMapper.MAX_VALUE + 1;
							illegal = true;
							return;
						}
					}
				}
				if (bbMapper.cores[proc1].getFromBandwidthRequirement()[proc2] > 0) {
					for (int j = 0; j < bbMapper.linkUsageList[tile2][tile1]
							.size(); j++) {
						int linkId = bbMapper.linkUsageList[tile2][tile1]
								.get(j);
						linkBandwidthUsage[linkId] += bbMapper.cores[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (linkBandwidthUsage[linkId] > bbMapper.links[linkId]
								.getBandwidth()) {
							cost = BranchAndBoundMapper.MAX_VALUE + 1;
							illegal = true;
							return;
						}
					}
				}
			}
		}

		stage++;

		if (calcBound) {
			tileOccupancyTable = new boolean[bbMapper.nodes.length];
			for (int i = 0; i < bbMapper.nodes.length; i++) {
				tileOccupancyTable[i] = false;
			}
	
			lowerBound = LowerBound();
			upperBound = UpperBound();
		}
		
	}

	/**
	 * Constructor
	 * 
	 * @param bbMapper
	 *            the {@link BranchAndBoundMapper} using this mapping node
	 *            (cannot be <tt>null</tt>)
	 * @param tileId
	 *            the ID of the tile to which this node is attached to
	 */
	public MappingNode(final BranchAndBoundMapper bbMapper, int tileId) {
		logger.assertLog(bbMapper != null,
				"The mapping node must be associated to a BranchAndBoundMapper");
		this.bbMapper = bbMapper;
		id = cnt;
		if (logger.isDebugEnabled()) {
			logger.debug("Creating mapping node with ID " + id + " (tileId " + tileId + ")");
		}

		illegal = false;
		tileOccupancyTable = null;
		mapping = null;
		linkBandwidthUsage = null;
		rSynLinkBandwidthUsage = null;
		rSynLinkBandwidthUsageTemp = null;
		
		if (bbMapper.buildRoutingTable) {
			routingTable = new int[bbMapper.hSize][bbMapper.nodes.length / bbMapper.hSize][bbMapper.nodes.length][bbMapper.nodes.length];
			for (int i = 0; i < routingTable.length; i++) {
				for (int j = 0; j < routingTable[i].length; j++) {
					for (int k = 0; k < routingTable[i][j].length; k++) {
						for (int l = 0; l < routingTable[i][j][k].length; l++) {
							routingTable[i][j][k][l] = -2;
						}
					}
				}
			}
		}
		
		occupancyTableReady = false;
		lowerBound = -1;

		routingBitArray = null;
		bestRoutingBitArray = null;

		cnt++;
		mapping = new int[bbMapper.cores.length];
		for (int i = 0; i < bbMapper.cores.length; i++) {
			mapping[i] = -1;
		}

		stage = 1;
		mapping[0] = tileId;
		next = null;
		cost = 0;

		// int proc1 = proc_map_array[0];
		// if (bbMapper.cores[proc1]->is_locked() &&
		// bbMapper.cores[proc1]->lock_to !=
		// tileId) {
		// illegal = true;
		// return;
		// }

		tileOccupancyTable = new boolean[bbMapper.nodes.length];
		for (int i = 0; i < bbMapper.nodes.length; i++) {
			tileOccupancyTable[i] = false;
		}

		if (bbMapper.buildRoutingTable) {
			rSynLinkBandwidthUsage = new int[bbMapper.hSize][bbMapper.nodes.length / bbMapper.hSize][4];
			for (int i = 0; i < bbMapper.hSize; i++) {
				for (int j = 0; j < bbMapper.nodes.length / bbMapper.hSize; j++) {
					for (int k = 0; k < 4; k++) {
						rSynLinkBandwidthUsage[i][j][k] = 0;
					}
				}
			}
		} else {
			linkBandwidthUsage = new int[bbMapper.links.length];
			for (int i = 0; i < bbMapper.links.length; i++) {
				linkBandwidthUsage[i] = 0;
			}
		}

		lowerBound = LowerBound();
		upperBound = UpperBound();
	}

	/**
	 * Copy constructor
	 * 
	 * <p>
	 * essentially, this is to generate a mapping node which is a copy of the
	 * node origin
	 * </p>
	 * 
	 * @param bbMapper
	 *            the {@link BranchAndBoundMapper} using this mapping node
	 *            (cannot be <tt>null</tt>)
	 * @param origin
	 *            the original node
	 */
	public MappingNode(final BranchAndBoundMapper bbMapper,
			final MappingNode origin) {
		logger.assertLog(bbMapper != null,
				"The mapping node must be associated to a BranchAndBoundMapper");
		this.bbMapper = bbMapper;
		id = cnt;
		if (logger.isDebugEnabled()) {
			logger.debug("Creating mapping node with ID " + id
					+ ", as copy of node " + origin.id);
		}

		tileOccupancyTable = null;
		mapping = null;
		linkBandwidthUsage = null;
		rSynLinkBandwidthUsage = null;
		rSynLinkBandwidthUsageTemp = null;
		
		if (bbMapper.buildRoutingTable) {
			routingTable = new int[bbMapper.hSize][bbMapper.nodes.length / bbMapper.hSize][bbMapper.nodes.length][bbMapper.nodes.length];
			for (int i = 0; i < routingTable.length; i++) {
				for (int j = 0; j < routingTable[i].length; j++) {
					for (int k = 0; k < routingTable[i][j].length; k++) {
						for (int l = 0; l < routingTable[i][j][k].length; l++) {
							routingTable[i][j][k][l] = -2;
						}
					}
				}
			}
		}

		routingBitArray = null;
		bestRoutingBitArray = null;

		occupancyTableReady = false;
		lowerBound = -1;
		cnt++;
		mapping = new int[bbMapper.cores.length];
		for (int i = 0; i < bbMapper.cores.length; i++) {
			mapping[i] = -1;
		}
		stage = origin.stage;
		illegal = origin.illegal;

		// Copy the parent's partial mapping
		for (int i = 0; i < bbMapper.cores.length; i++) {
			mapping[i] = origin.mapping[i];
		}

		if (bbMapper.buildRoutingTable) {
			// Copy the parent's link bandwidth usage
			rSynLinkBandwidthUsage = new int[bbMapper.hSize][bbMapper.nodes.length / bbMapper.hSize][4];
			for (int i = 0; i < bbMapper.hSize; i++) {
				for (int j = 0; j < bbMapper.nodes.length / bbMapper.hSize; j++) {
					rSynLinkBandwidthUsage[i][j] = Arrays.copyOf(
							origin.rSynLinkBandwidthUsage[i][j], 4);
				}
			}
		}
	}

	/**
	 * This calculates the lower bound cost of the unmapped process nodes in the
	 * current mapping
	 */
	private float LowerBound() {
		for (int i = 0; i < bbMapper.nodes.length; i++) {
			tileOccupancyTable[i] = false;
		}
		for (int i = 0; i < stage; i++) {
			tileOccupancyTable[mapping[i]] = true;
		}

		occupancyTableReady = true;
		lowerBound = cost;

		// The first part of the cost is the communication between those
		// nodes that have been mapped and those have not yet.
		// We assume that the unmapped node can occupy the unoccupied tile
		// which has the lowest cost to the occupied node
		for (int i = 0; i < stage; i++) {
			for (int j = stage; j < bbMapper.cores.length; j++) {
				if (bbMapper.procMatrix[i][j] == 0) {
					continue;
				}
				else {
					lowerBound += bbMapper.procMatrix[i][j]
							* lowestUnitCost(mapping[i]);
				}
			}
		}
		// Now add the cost of the communication among all the un-mapped nodes
		int vol = 0;
		for (int i = stage; i < bbMapper.cores.length; i++) {
			for (int j = i + 1; j < bbMapper.cores.length; j++) {
				vol += bbMapper.procMatrix[i][j];
			}
		}
		lowerBound += vol * lowestUnmappedUnitCost();
		if (logger.isDebugEnabled()) {
			logger.debug("Lower bound " + lowerBound);
		}
		return lowerBound;
	}

	/**
	 * This calculates the upper bound cost of the this partial mapping in the
	 * current mapping
	 */
	private float UpperBound() {
		if (!occupancyTableReady) {
			for (int i = 0; i < bbMapper.nodes.length; i++) {
				tileOccupancyTable[i] = false;
			}
			for (int i = 0; i < stage; i++) {
				tileOccupancyTable[mapping[i]] = true;
			}
		}

		greedyMapping();
		if (logger.isTraceEnabled()) {
			logger.trace("Initial upper bound is " + cost);
		}
		upperBound = cost;

		illegalChildMapping = false;

		if (bbMapper.buildRoutingTable) {
			createBandwidthTempMemory();
			if (!routeTraffics(stage, bbMapper.cores.length - 1, false, true)) {
				illegalChildMapping = true;
				if (logger.isTraceEnabled()) {
					logger.trace("Upper bound is the max value " + BranchAndBoundMapper.MAX_VALUE);
				}
				upperBound = BranchAndBoundMapper.MAX_VALUE;
				return upperBound;
			}
		} else {
			if (!fixedVerifyBandwidthUsage()) {
				illegalChildMapping = true;
				if (logger.isTraceEnabled()) {
					logger.trace("Upper bound is the max value " + BranchAndBoundMapper.MAX_VALUE);
				}
				upperBound = BranchAndBoundMapper.MAX_VALUE;
				return upperBound;
			}
		}

		for (int i = 0; i < stage; i++) {
			int tile1 = mapping[i];
			for (int j = stage; j < bbMapper.cores.length; j++) {
				int tile2 = mapping[j];
				if (logger.isTraceEnabled()) {
					logger.trace("Adding to the upper bound "
							+ bbMapper.procMatrix[i][j]
							* bbMapper.archMatrix[tile1][tile2]
							+ "(procMatrix[" + i + "][" + j + "] = "
							+ bbMapper.procMatrix[i][j] + " archMatrix["
							+ tile1 + "][" + tile2 + "] = "
							+ bbMapper.archMatrix[tile1][tile2] + ")");
				}
				upperBound += bbMapper.procMatrix[i][j]
						* bbMapper.archMatrix[tile1][tile2];
			}
		}
		for (int i = stage; i < bbMapper.cores.length; i++) {
			int tile1 = mapping[i];
			for (int j = i + 1; j < bbMapper.cores.length; j++) {
				int tile2 = mapping[j];
				if (logger.isTraceEnabled()) {
					logger.trace("Adding to the upper bound "
							+ bbMapper.procMatrix[i][j]
							* bbMapper.archMatrix[tile1][tile2]
							+ "(procMatrix[" + i + "][" + j + "] = "
							+ bbMapper.procMatrix[i][j] + " archMatrix["
							+ tile1 + "][" + tile2 + "] = "
							+ bbMapper.archMatrix[tile1][tile2] + ")");
				}
				upperBound += bbMapper.procMatrix[i][j]
						* bbMapper.archMatrix[tile1][tile2];
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Upper bound " + upperBound);
		}
		return upperBound;
	}

	private boolean fixedVerifyBandwidthUsage() {
		int[] linkBandwidthUsageTemp = new int[bbMapper.links.length];
		linkBandwidthUsageTemp = Arrays.copyOf(linkBandwidthUsage,
				bbMapper.links.length);

		for (int i = 0; i < stage; i++) {
			int tile1 = mapping[i];
			int proc1 = bbMapper.procMapArray[i];
			for (int j = stage; j < bbMapper.cores.length; j++) {
				int tile2 = mapping[j];
				int proc2 = bbMapper.procMapArray[j];
				if (bbMapper.cores[proc1].getToBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < bbMapper.linkUsageList[tile1][tile2]
							.size(); k++) {
						int linkId = bbMapper.linkUsageList[tile1][tile2]
								.get(k);
						linkBandwidthUsageTemp[linkId] += bbMapper.cores[proc1]
								.getToBandwidthRequirement()[proc2];
						if (linkBandwidthUsageTemp[linkId] > bbMapper.links[linkId]
								.getBandwidth()) {
							return false;
						}
					}
				}

				if (bbMapper.cores[proc1].getFromBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < bbMapper.linkUsageList[tile2][tile1]
							.size(); k++) {
						int linkId = bbMapper.linkUsageList[tile2][tile1]
								.get(k);
						linkBandwidthUsageTemp[linkId] += bbMapper.cores[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (linkBandwidthUsageTemp[linkId] > bbMapper.links[linkId]
								.getBandwidth()) {
							return false;
						}
					}
				}
			}
		}
		for (int i = stage; i < bbMapper.cores.length; i++) {
			int tile1 = mapping[i];
			int proc1 = bbMapper.procMapArray[i];
			for (int j = i + 1; j < bbMapper.cores.length; j++) {
				int tile2 = mapping[j];
				int proc2 = bbMapper.procMapArray[j];
				if (bbMapper.cores[proc1].getToBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < bbMapper.linkUsageList[tile1][tile2]
							.size(); k++) {
						int linkId = bbMapper.linkUsageList[tile1][tile2]
								.get(k);
						linkBandwidthUsageTemp[linkId] += bbMapper.cores[proc1]
								.getToBandwidthRequirement()[proc2];
						if (linkBandwidthUsageTemp[linkId] > bbMapper.links[linkId]
								.getBandwidth()) {
							return false;
						}
					}
				}

				if (bbMapper.cores[proc1].getFromBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < bbMapper.linkUsageList[tile2][tile1]
							.size(); k++) {
						int linkId = bbMapper.linkUsageList[tile2][tile1]
								.get(k);
						linkBandwidthUsageTemp[linkId] += bbMapper.cores[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (linkBandwidthUsageTemp[linkId] > bbMapper.links[linkId]
								.getBandwidth()) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	/**
	 * @return the tile to be mapped for the next node which will lead to the
	 *         smallest partial mapping cost
	 */
	int bestCostCandidate() {
		float minimal = BranchAndBoundMapper.MAX_VALUE;
		for (int i = 0; i < bbMapper.nodes.length; i++) {
			tileOccupancyTable[i] = false;
		}
		for (int i = 0; i < stage; i++) {
			tileOccupancyTable[mapping[i]] = true;
		}

		int index = -1;
		for (int tileId = 0; tileId < bbMapper.nodes.length; tileId++) {
			if (tileOccupancyTable[tileId]) {
				continue;
			}
			float additionalCost = 0;
			for (int i = 0; i < stage; i++) {
				int tile1 = tileId;
				int tile2 = mapping[i];
				additionalCost += bbMapper.procMatrix[i][stage]
						* bbMapper.archMatrix[tile1][tile2];
				if (MathUtils.definitelyGreaterThan(additionalCost, minimal)
						|| MathUtils.approximatelyEqual(additionalCost, minimal))
					break;
			}
			if (MathUtils.definitelyLessThan(additionalCost, minimal)) {
				minimal = additionalCost;
			}
			index = tileId;
		}
		return index;
	}

	int mapToNode(int i) {
		return mapping[i];
	}

	/**
	 * @return the tile to be mapped for the next node with the criteria of the
	 *         greedy mapping of the current one.
	 */
	int bestUpperBoundCandidate() {
		return mapping[stage];
	}

	/**
	 * @return the lowest cost of the tileId to any unoccupied tile.
	 */
	private float lowestUnitCost(int tileId) {
		float min = 50000;
		for (int i = 0; i < bbMapper.nodes.length; i++) {
			if (i == tileId) {
				continue;
			}
			if (tileOccupancyTable[i]) {
				continue;
			}
			if (MathUtils.definitelyLessThan(bbMapper.archMatrix[tileId][i], min)) {
				min = bbMapper.archMatrix[tileId][i];
			}
		}
		return min;
	}

	/**
	 * 
	 * @return the lowest cost between any two unoccupied tiles
	 */
	private float lowestUnmappedUnitCost() {
		float min = 50000;
		for (int i = 0; i < bbMapper.nodes.length; i++) {
			if (tileOccupancyTable[i]) {
				continue;
			}
			for (int j = i + 1; j < bbMapper.nodes.length; j++) {
				if (tileOccupancyTable[j]) {
					continue;
				}
				if (MathUtils.definitelyLessThan(bbMapper.archMatrix[i][j], min)) {
					min = bbMapper.archMatrix[i][j];
				}
			}
		}
		return min;
	}

	/**
	 * Map the other unmapped process node using greedy mapping
	 */
	private void greedyMapping() {
		for (int i = stage; i < bbMapper.cores.length; i++) {
			int sumRow = 0;
			int sumCol = 0;
			int vol = 0;
			for (int j = 0; j < i; j++) {
				if (bbMapper.procMatrix[i][j] == 0) {
					continue;
				}
				int tileId = mapping[j];
				int row = tileId / bbMapper.hSize;
				int col = tileId % bbMapper.hSize;
				sumRow += bbMapper.procMatrix[i][j] * row;
				sumCol += bbMapper.procMatrix[i][j] * col;
				vol += bbMapper.procMatrix[i][j];
			}
			// This is somehow the ideal position
			float myRow, myCol;
			if (vol == 0) {
				myRow = -1;
				myCol = -1;
			} else {
				myRow = ((float) sumRow) / vol;
				myCol = ((float) sumCol) / vol;
			}
			mapNode(i, myRow, myCol);
		}
	}

	/**
	 * Try to map the node to an unoccupied tile which is closest to the
	 * tile(goodRow, goodCol)
	 * 
	 * @param procId
	 *            the ID of the process to be mapped
	 * @param goodRow
	 *            the row of the tile
	 * @param goodCol
	 *            the column of the tile
	 */
	private void mapNode(int procId, float goodRow, float goodCol) {
		float minDist = 10000;
		int bestId = -1;
		for (int i = 0; i < bbMapper.nodes.length; i++) {
			if (logger.isTraceEnabled()) {
				logger.trace("tileOccupancyTable[" + i + "] = " + (tileOccupancyTable[i] ? "1" : "0"));
			}
			if (tileOccupancyTable[i]) {
				continue;
			}
			if (MathUtils.definitelyLessThan(goodRow, 0)) {
				bestId = i;
				if (logger.isTraceEnabled()) {
					logger.trace("bestId " + bestId);
				}
				break;
			}
			int row = i / bbMapper.hSize;
			int col = i % bbMapper.hSize;
			float dist = Math.abs(goodRow - row) + Math.abs(goodCol - col);
			// Note that we use machine epsilon to perform the following
			// comparison between the float numbers
			if (MathUtils.definitelyLessThan(dist, minDist)) {
				if (logger.isTraceEnabled()) {
					logger.trace("bestId " + i + " dist " + dist + " (old) minDist " + minDist);
				}
				minDist = dist;
				bestId = i;
			}
		}
		mapping[procId] = bestId;
		if (logger.isTraceEnabled()) {
			logger.trace("mappingSequency[" + procId + "] = " + bestId + "(goodRow " + goodRow + " goodCol " + goodCol + ")");
		}
		tileOccupancyTable[bestId] = true;
	}

	boolean isExpandable(int tileId) {
		boolean expandable = true;
		// If it's an illegal mapping, then just return false
		for (int i = 0; i < stage; i++) {
			if (mapping[i] == tileId) {
				// the tile has already been occupied
				expandable = false;
				break;
			}
		}
		return expandable;
	}

	void printMapping() {
		if (logger.isDebugEnabled()) {
			for (int i = 0; i < mapping.length; i++) {
				logger.debug("Core " + mapping[i] + " is mapped to node " + i);
			}
		}
	}
	
	/**
	 * @return whether this node is illegal or not
	 */
	public boolean isIllegal() {
		if (illegal) {
			if (logger.isDebugEnabled()) {
				logger.debug("The following mapping is illegal (cost " + cost + ")");
				printMapping();
			}
		}
		return illegal;
	}

	/**
	 * @return the depth at which this node is placed in the search tree
	 */
	int getStage() {
		return stage;
	}
	
	/**
	 * @return the unique identifier of this node
	 */
	public int getId() {
		return id;
	}

	private void createBandwidthTempMemory() {
		// Copy the bandwidth usage status to rSynLinkBandwidthUsageTemp
		rSynLinkBandwidthUsageTemp = new int[bbMapper.hSize][bbMapper.nodes.length / bbMapper.hSize][4];
		for (int i = 0; i < bbMapper.hSize; i++) {
			for (int j = 0; j < bbMapper.nodes.length / bbMapper.hSize; j++) {
				rSynLinkBandwidthUsageTemp[i][j] = Arrays.copyOf(
						rSynLinkBandwidthUsage[i][j], 4);
			}
		}
	}

	/**
	 * fixing the routing tables of the tiles which are occupied by the
	 * processes from beginStage to endStage
	 * 
	 * @param beginStage
	 *            the begin stage
	 * @param endStage
	 *            the end stage
	 * @param commit
	 * @param updateRoutingTable
	 * @return
	 */
	private boolean routeTraffics(int beginStage, int endStage, boolean commit,
			boolean updateRoutingTable) {
		List<ProcComm> Q = new ArrayList<ProcComm>();
		for (int currentStage = beginStage; currentStage <= endStage; currentStage++) {
			int newProc = bbMapper.procMapArray[currentStage];
			// Sort the request in the queue according to the BW
			// However, if the src and the dst are in the same row or in the
			// same column,
			// then we should insert it at the head of the queue.
			for (int i = 0; i < currentStage; i++) {
				int oldProc = bbMapper.procMapArray[i];
				if (bbMapper.cores[newProc].getToBandwidthRequirement()[oldProc] != 0) {
					ProcComm aProcComm = new ProcComm();
					aProcComm.srcProc = currentStage; // we put virtual proc id
					aProcComm.dstProc = i;
					aProcComm.bandwidth = bbMapper.cores[newProc]
							.getToBandwidthRequirement()[oldProc];
					aProcComm.adaptivity = calculateAdaptivity(
							mapping[aProcComm.srcProc],
							mapping[aProcComm.dstProc],
							aProcComm.bandwidth);
					for (Iterator<ProcComm> iterator = Q.iterator(); iterator
							.hasNext();) {
						ProcComm procComm = (ProcComm) iterator.next();
						if ((aProcComm.adaptivity < procComm.adaptivity)
								|| (aProcComm.adaptivity == procComm.adaptivity && aProcComm.bandwidth > procComm.bandwidth))
							break;
					}
					Q.add(aProcComm);
				}
				if (bbMapper.cores[newProc].getFromBandwidthRequirement()[oldProc] > 0) {
					ProcComm aProcComm = new ProcComm();
					aProcComm.srcProc = i;
					aProcComm.dstProc = currentStage;
					aProcComm.bandwidth = bbMapper.cores[newProc]
							.getFromBandwidthRequirement()[oldProc];
					aProcComm.adaptivity = calculateAdaptivity(
							mapping[aProcComm.srcProc],
							mapping[aProcComm.dstProc],
							aProcComm.bandwidth);
					for (Iterator<ProcComm> iterator = Q.iterator(); iterator
							.hasNext();) {
						ProcComm procComm = (ProcComm) iterator.next();
						if ((aProcComm.adaptivity < procComm.adaptivity)
								|| (aProcComm.adaptivity == procComm.adaptivity && aProcComm.bandwidth > procComm.bandwidth))
							break;
					}
					Q.add(aProcComm);
				}
			}
		}
		// now route the traffic
		for (int i = 0; i < Q.size(); i++) {
			int srcProc = Q.get(i).srcProc;
			int dstProc = Q.get(i).dstProc;
			int srcTile = mapping[srcProc];
			int dstTtile = mapping[dstProc];
			long bandwidth = Q.get(i).bandwidth;
			if (RoutingEffort.EASY.equals(bbMapper.routingEffort)) {
				if (!routeTrafficEasy(srcTile, dstTtile, bandwidth, commit,
						updateRoutingTable)) {
					return false;
				}
			} else {
				if (!routeTrafficHard(srcTile, dstTtile, bandwidth, commit,
						updateRoutingTable)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * currently there is only two levels of adaptivity. 0 for no adaptivity, 1
	 * for (maybe) some adaptivity
	 * 
	 * @param srcTile
	 *            the source node
	 * @param dstTile
	 *            the destination node
	 * @param bandwidth
	 *            the bandwidth
	 * @return the computed adaptivity
	 */
	private final int calculateAdaptivity(int srcTile, int dstTile,
			long bandwidth) {
		int adaptivity;
		int rowSrc = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[srcTile], BandwidthConstrainedEnergyAndPerformanceAwareMapper.TopologyParameter.ROW));
		int colSrc = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[srcTile], COLUMN));
		int rowDst = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[dstTile], ROW));
		int colDst = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[dstTile], COLUMN));
		
		int row = rowSrc;
		int col = colSrc;
		if (row == rowDst || col == colDst) {
			adaptivity = 0;
			return adaptivity;
		}

		int[][][] bandwidthUsage = rSynLinkBandwidthUsage;

		adaptivity = 1;
		int direction = -2;
		while (row != rowDst || col != colDst) {
			// For west-first routing
			if (BranchAndBoundMapper.LegalTurnSet.WEST_FIRST
					.equals(bbMapper.legalTurnSet)) {
				if (col > colDst) // step west
					return 0;
				else if (col == colDst)
					return 0;
				else if (row == rowDst)
					return 0;
				// Here comes the flexibility. We can choose whether to go
				// vertical or horizontal
				else {
					int direction1 = (row < rowDst) ? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
					if (bandwidthUsage[row][col][direction1] + bandwidth < bbMapper.linkBandwidth
							&& bandwidthUsage[row][col][BranchAndBoundMapper.EAST]
									+ bandwidth < bbMapper.linkBandwidth)
						return 1;
					direction = (bandwidthUsage[row][col][direction1] < bandwidthUsage[row][col][BranchAndBoundMapper.EAST]) ? direction1
							: BranchAndBoundMapper.EAST;
				}
			}
			// For odd-even routing
			else if (BranchAndBoundMapper.LegalTurnSet.ODD_EVEN
					.equals(bbMapper.legalTurnSet)) {
				int e0 = colDst - col;
				int e1 = rowDst - row;
				if (e0 == 0) // currently the same column as destination
					direction = (e1 > 0) ? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
				else {
					if (e0 > 0) { // eastbound messages
						if (e1 == 0)
							direction = BranchAndBoundMapper.EAST;
						else {
							int direction1 = -1, direction2 = -1;
							if (col % 2 == 1
									|| col == colSrc)
								direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
										: BranchAndBoundMapper.SOUTH;
							if (colDst % 2 == 1
									|| e0 != 1)
								direction2 = BranchAndBoundMapper.EAST;
							if (direction1 == -1)
								direction = direction2;
							else if (direction2 == -1)
								direction = direction1;
							else {// we have two choices
								if (bandwidthUsage[row][col][direction1]
										+ bandwidth < bbMapper.linkBandwidth
										&& bandwidthUsage[row][col][direction2]
												+ bandwidth < bbMapper.linkBandwidth)
									return 1;
								direction = (bandwidthUsage[row][col][direction1] < bandwidthUsage[row][col][direction2]) ? direction1
										: direction2;
							}
						}
					} else { // westbound messages
						if (col % 2 != 0 || e1 == 0)
							direction = BranchAndBoundMapper.WEST;
						else {
							int direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
									: BranchAndBoundMapper.SOUTH;
							if (bandwidthUsage[row][col][direction1]
									+ bandwidth < bbMapper.linkBandwidth
									&& bandwidthUsage[row][col][BranchAndBoundMapper.WEST]
											+ bandwidth < bbMapper.linkBandwidth)
								return 1;
							direction = (bandwidthUsage[row][col][BranchAndBoundMapper.WEST] < bandwidthUsage[row][col][direction1]) ? BranchAndBoundMapper.WEST
									: direction1;
						}
					}
				}
			}
			switch (direction) {
			case BranchAndBoundMapper.SOUTH:
				row--;
				break;
			case BranchAndBoundMapper.NORTH:
				row++;
				break;
			case BranchAndBoundMapper.EAST:
				col++;
				break;
			case BranchAndBoundMapper.WEST:
				col--;
				break;
			default:
				logger.error("Error: unknown direction");
				break;
			}
		}
		return 0;
	}

	/**
	 * Route the traffic from srcTile to dstTile using bandwidth. Two routing
	 * methods are provided: west-first and odd-even routing.
	 * 
	 * @param srcTile
	 *            the source node
	 * @param dstTile
	 *            the destination node
	 * @param bandwidth
	 *            the bandwidth
	 * @param commit
	 *            whether or not the computed bandwidth usage will be applied
	 * @param updateRoutingTable
	 *            whether or not this routing will be applied
	 * @return whether or not the routing has been successful
	 */
	private boolean routeTrafficEasy(int srcTile, int dstTile, long bandwidth,
			boolean commit, boolean updateRoutingTable) {
		int rowSrc = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[srcTile], ROW));
		int colSrc = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[srcTile], COLUMN));
		int rowDst = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[dstTile], ROW));
		int colDst = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[dstTile], COLUMN));

		int row = rowSrc;
		int col = colSrc;
		int[][][] bandwidthUsage = commit ? rSynLinkBandwidthUsage
				: rSynLinkBandwidthUsageTemp;
		int direction = -2;
		while (row != rowDst
				|| col != colDst) {
			// For west-first routing
			if (BranchAndBoundMapper.LegalTurnSet.WEST_FIRST
					.equals(bbMapper.legalTurnSet)) {
				if (col > colDst) // step west
					direction = BranchAndBoundMapper.WEST;
				else if (col == colDst)
					direction = (row < rowDst) 
							? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
				else if (row == rowDst)
					direction = BranchAndBoundMapper.EAST;
				// Here comes the flexibility. We can choose whether to go
				// vertical or horizontal

				/*
				 * else { int direction1 =
				 * (row<dst.row)?BranchAndBoundMapper.NORTH
				 * :BranchAndBoundMapper.SOUTH; if
				 * (BW_usage[row][col][direction1] <
				 * BW_usage[row][col][BranchAndBoundMapper.EAST]) direction =
				 * direction1; else if (BW_usage[row][col][direction1] >
				 * BW_usage[row][col][BranchAndBoundMapper.EAST]) direction =
				 * BranchAndBoundMapper.EAST; else { //In this case, we select
				 * the direction which has the longest //distance to the
				 * destination if ((dst.col-col)*(dst.col-col) <=
				 * (dst.row-row)*(dst.row-row)) direction = direction1; else
				 * //Horizontal move direction = BranchAndBoundMapper.EAST; } }
				 */
				else {
					direction = BranchAndBoundMapper.EAST;
					if (bandwidthUsage[row][col][direction] + bandwidth > bbMapper.linkBandwidth)
						direction = (row < rowDst) 
								? BranchAndBoundMapper.NORTH
								: BranchAndBoundMapper.SOUTH;
				}
			}
			// For odd-even routing
			else if (BranchAndBoundMapper.LegalTurnSet.ODD_EVEN
					.equals(bbMapper.legalTurnSet)) {
				int e0 = colDst - col;
				int e1 = rowDst - row;
				if (e0 == 0) // currently the same column as destination
					direction = (e1 > 0) ? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
				else {
					if (e0 > 0) { // eastbound messages
						if (e1 == 0)
							direction = BranchAndBoundMapper.EAST;
						else {
							int direction1 = -1, direction2 = -1;
							if (col % 2 == 1
									|| col == colSrc)
								direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
										: BranchAndBoundMapper.SOUTH;
							if (colDst % 2 == 1
									|| e0 != 1)
								direction2 = BranchAndBoundMapper.EAST;
							if (direction1 == -1 && direction2 == -1) {
								logger.fatal("Error");
								System.exit(1);
							}
							if (direction1 == -1)
								direction = direction2;
							else if (direction2 == -1)
								direction = direction1;
							else
								// we have two choices
								direction = (bandwidthUsage[row][col][direction1] < bandwidthUsage[row][col][direction2]) ? direction1
										: direction2;
						}
					} else { // westbound messages
						if (col % 2 != 0 || e1 == 0)
							direction = BranchAndBoundMapper.WEST;
						else {
							int direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
									: BranchAndBoundMapper.SOUTH;
							direction = (bandwidthUsage[row][col][BranchAndBoundMapper.WEST] < bandwidthUsage[row][col][direction1]) ? BranchAndBoundMapper.WEST
									: direction1;
						}
					}
				}
			}

			bandwidthUsage[row][col][direction] += bandwidth;
			if (bandwidthUsage[row][col][direction] > bbMapper.linkBandwidth
					&& (!updateRoutingTable))
				return false;
			if (updateRoutingTable)
				routingTable[row][col][srcTile][dstTile] = direction;

			switch (direction) {
			case BranchAndBoundMapper.SOUTH:
				row--;
				break;
			case BranchAndBoundMapper.NORTH:
				row++;
				break;
			case BranchAndBoundMapper.EAST:
				col++;
				break;
			case BranchAndBoundMapper.WEST:
				col--;
				break;
			default:
				logger.error("Error: unknown direction");
				break;
			}
		}
		return true;
	}

	/**
	 * Route the traffic from srcTile to dstTile using bandwidth in complex
	 * model, by which it means select the path from all its candidate paths
	 * which has the minimal maximal bandwidth usage.
	 * 
	 * @param srcTile
	 *            the source node
	 * @param dstTile
	 *            the destination node
	 * @param bandwidth
	 *            the bandwidth
	 * @param commit
	 *            whether or not the computed bandwidth usage will be applied
	 * @param updateRoutingTable
	 *            whether or not this routing will be applied
	 * @return whether or not the routing has been successful
	 */
	private boolean routeTrafficHard(int srcTile, int dstTile, long bandwidth,
			boolean commit, boolean updateRoutingTable) {
		int rowSrc = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[srcTile], ROW));
		int colSrc = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[srcTile], COLUMN));
		int rowDst = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[dstTile], ROW));
		int colDst = Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[dstTile], COLUMN));

		int row = rowSrc;
		int col = colSrc;
		int[][][] BW_usage = commit ? rSynLinkBandwidthUsage
				: rSynLinkBandwidthUsageTemp;

		// We can arrive at any destination with bbMapper.hSize + bbMapper.nodes.length / bbMapper.hSize hops
		if (routingBitArray == null) {
			routingBitArray = new int[bbMapper.hSize + bbMapper.nodes.length / bbMapper.hSize];
		}
		if (bestRoutingBitArray == null) {
			bestRoutingBitArray = new int[bbMapper.hSize + bbMapper.nodes.length / bbMapper.hSize];
		}

		// In the following, we find the routing path which has the minimal
		// maximal
		// link BW usage and store that routing path to best_routing_bit_array
		int min_path_BW = Integer.MAX_VALUE;
		int x_hop = colSrc - colDst;
		x_hop = (x_hop >= 0) ? x_hop : (0 - x_hop);
		int y_hop = rowSrc - rowDst;
		y_hop = (y_hop >= 0) ? y_hop : (0 - y_hop);

		initRoutingPathGenerator(x_hop, y_hop);

		while (nextRoutingPath(x_hop, y_hop)) { // For each path
			int usage = pathBandwidthUsage(row, col,
					rowDst,
					colDst, BW_usage, bandwidth);
			if (usage < min_path_BW) {
				min_path_BW = usage;
				bestRoutingBitArray = Arrays.copyOf(routingBitArray, x_hop
						+ y_hop);
			}
		}

		if (min_path_BW == Integer.MAX_VALUE)
			return false;

		int direction = -2;

		int hop_id = 0;
		while (row != rowDst || col != colDst) {
			if (bestRoutingBitArray[hop_id++] != 0)
				direction = (row < rowDst) ? BranchAndBoundMapper.NORTH
						: BranchAndBoundMapper.SOUTH;
			else
				direction = (col < colDst) ? BranchAndBoundMapper.EAST
						: BranchAndBoundMapper.WEST;

			BW_usage[row][col][direction] += bandwidth;

			if ((BW_usage[row][col][direction] > bbMapper.linkBandwidth)
					&& (!updateRoutingTable))
				return false;
			if (updateRoutingTable)
				routingTable[row][col][srcTile][dstTile] = direction;

			switch (direction) {
			case BranchAndBoundMapper.SOUTH:
				row--;
				break;
			case BranchAndBoundMapper.NORTH:
				row++;
				break;
			case BranchAndBoundMapper.EAST:
				col++;
				break;
			case BranchAndBoundMapper.WEST:
				col--;
				break;
			default:
				logger.error("Error: unknown direction");
				break;
			}
		}
		return true;
	}

	/**
	 * Fulfills two tasks. First, check to see whether it's a valid path
	 * according to the selected routing algorithm. Second, it checks to see if
	 * any bandwidth requirement violates. If either of the two conditions is
	 * not met, return {@link Integer#MAX_VALUE}.
	 * 
	 * @param srcRow
	 *            the source row
	 * @param srcColumn
	 *            the source column
	 * @param dstRow
	 *            the destination row
	 * @param dstColumn
	 *            the destination column
	 * @param bandwidthUsage
	 *            the bandwidth usage
	 * @param bandwidth
	 *            the bandwidth
	 * @return 1, when everything is OK, {@link Integer#MAX_VALUE} otherwise
	 */
	private int pathBandwidthUsage(int srcRow, int srcColumn, int dstRow,
			int dstColumn, int[][][] bandwidthUsage, long bandwidth) {
		int row = srcRow;
		int col = srcColumn;

		int max_BW = 0;
		int hop_id = 0;

		while (row != dstRow || col != dstColumn) {
			int direction = -2;
			// For west-first routing
			if (BranchAndBoundMapper.LegalTurnSet.WEST_FIRST
					.equals(bbMapper.legalTurnSet)) {
				if (col > dstColumn) { // step west
					direction = BranchAndBoundMapper.WEST;
					if (routingBitArray[hop_id] != 0)
						return Integer.MAX_VALUE;
				} else if (col == dstColumn) {
					direction = (row < dstRow) ? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
					if (routingBitArray[hop_id] == 0)
						return Integer.MAX_VALUE;
				} else if (row == dstRow) {
					direction = BranchAndBoundMapper.EAST;
					if (routingBitArray[hop_id] != 0)
						return Integer.MAX_VALUE;
				}
				// Here comes the flexibility. We can choose whether to go
				// vertical or horizontal
				else {
					int direction1 = (row < dstRow) ? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
					int direction2 = BranchAndBoundMapper.EAST;
					direction = (routingBitArray[hop_id] != 0) ? direction1
							: direction2;
				}
			}
			// For odd-even routing
			else if (BranchAndBoundMapper.LegalTurnSet.ODD_EVEN
					.equals(bbMapper.legalTurnSet)) {
				int e0 = dstColumn - col;
				int e1 = dstRow - row;
				if (e0 == 0) { // currently the same column as destination
					direction = (e1 > 0) ? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
					if (routingBitArray[hop_id] == 0)
						return Integer.MAX_VALUE;
				} else {
					if (e0 > 0) { // eastbound messages
						if (e1 == 0) {
							direction = BranchAndBoundMapper.EAST;
							if (routingBitArray[hop_id] != 0)
								return Integer.MAX_VALUE;
						} else {
							int direction1 = -1, direction2 = -1;
							if (col % 2 == 1 || col == srcColumn)
								direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
										: BranchAndBoundMapper.SOUTH;
							if (dstColumn % 2 == 1 || e0 != 1)
								direction2 = BranchAndBoundMapper.EAST;
							logger.assertLog((!(direction1 == -1 && direction2 == -1)), null);
							if (direction1 == -1) {
								direction = direction2;
								if (routingBitArray[hop_id] != 0)
									return Integer.MAX_VALUE;
							} else if (direction2 == -1) {
								direction = direction1;
								if (routingBitArray[hop_id] == 0)
									return Integer.MAX_VALUE;
							} else
								// we have two choices
								direction = (routingBitArray[hop_id] != 0) ? direction1
										: direction2;
						}
					} else { // westbound messages
						if (col % 2 != 0 || e1 == 0) {
							direction = BranchAndBoundMapper.WEST;
							if (routingBitArray[hop_id] != 0)
								return Integer.MAX_VALUE;
						} else {
							int direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
									: BranchAndBoundMapper.SOUTH;
							int direction2 = BranchAndBoundMapper.WEST;
							direction = (routingBitArray[hop_id] != 0) ? direction1
									: direction2;
						}
					}
				}
			}

			if (bandwidthUsage[row][col][direction] > max_BW)
				max_BW = bandwidthUsage[row][col][direction];

			if (bandwidthUsage[row][col][direction] + bandwidth > bbMapper.linkBandwidth)
				return Integer.MAX_VALUE;

			switch (direction) {
			case BranchAndBoundMapper.SOUTH:
				row--;
				break;
			case BranchAndBoundMapper.NORTH:
				row++;
				break;
			case BranchAndBoundMapper.EAST:
				col++;
				break;
			case BranchAndBoundMapper.WEST:
				col--;
				break;
			default:
				logger.error("Error: unknown direction");
				break;
			}
			hop_id++;

		}
		return 1;
	}

	/**
	 * @param xHop
	 *            the number of hops on the X axis
	 * @param yHop
	 *            the number of hops on the Y axis
	 * @return <tt>true</tt>
	 */
	private boolean initRoutingPathGenerator(int xHop, int yHop) {
		firstRoutingPath = true;
		maxRoutingInt = 0;
		for (int index = 0; index < yHop; index++)
			maxRoutingInt = (maxRoutingInt << 1) + 1;
		maxRoutingInt = maxRoutingInt << xHop;
		return true;
	}

	/**
	 * @param xHop
	 *            the number of hops on the X axis
	 * @param yHop
	 *            the number of hops on the Y axis
	 * @return whether or not the next routing path was identified
	 */
	private boolean nextRoutingPath(int xHop, int yHop) {
		if (firstRoutingPath) {
			firstRoutingPath = false;
			int index = 0;
			routingInt = 0;
			for (index = 0; index < yHop; index++) {
				routingBitArray[index] = 1;
				routingInt = (routingInt << 1) + 1;
			}
			for (int x_index = 0; x_index < xHop; x_index++)
				routingBitArray[index + x_index] = 0;
			return true;
		}

		// find the next routing path based on the current routing bit array
		// the next one is the one which is the minimal array which is larger
		// than the current routing_bit_array but with the same number of 1s
		while (routingInt <= maxRoutingInt) {
			if (routingInt % 2 == 0) // For an even number
				routingInt += 2;
			else
				routingInt++;
			if (oneBits(routingInt, yHop))
				break;
		}
		if (routingInt <= maxRoutingInt)
			return true;
		else
			return false;
	}

	/**
	 * Returns true if the binary representation of r contains y_hop number of
	 * 1s. It also assigns the bit form to routingBitArray
	 * 
	 * @param r
	 * @param onebits
	 * @return
	 */
	private boolean oneBits(int r, int onebits) {
		routingBitArray = new int[bbMapper.hSize + bbMapper.nodes.length / bbMapper.hSize];
		Arrays.fill(routingBitArray, 0);
		int index = 0;
		int currentOneBits = 0;
		while (r != 0) {
			routingBitArray[index] = r & 1;
			if (routingBitArray[index] != 0)
				currentOneBits++;
			if (currentOneBits > onebits)
				return false;
			index++;
			r = r >> 1;
		}
		if (currentOneBits == onebits)
			return true;
		else
			return false;
	}

	boolean programRouters() {
		generateRoutingTable();
		// clean all the old routing table
		for (int tileId = 0; tileId < bbMapper.nodes.length; tileId++) {
			for (int srcTile = 0; srcTile < bbMapper.nodes.length; srcTile++) {
				for (int dstTile = 0; dstTile < bbMapper.nodes.length; dstTile++) {
					if (tileId == dstTile)
						bbMapper.routingTables[Integer.valueOf(bbMapper.nodes[tileId].getId())][srcTile][dstTile] = -1;
					else
						bbMapper.routingTables[Integer.valueOf(bbMapper.nodes[tileId].getId())][srcTile][dstTile] = -2;
				}
			}
		}

		for (int row = 0; row < bbMapper.hSize; row++) {
			for (int col = 0; col < bbMapper.nodes.length / bbMapper.hSize; col++) {
				int tileId = row * bbMapper.hSize + col;
				for (int srcTile = 0; srcTile < bbMapper.nodes.length; srcTile++) {
					for (int dstTile = 0; dstTile < bbMapper.nodes.length; dstTile++) {
						int linkId = locateLink(row, col,
								routingTable[row][col][srcTile][dstTile]);
						if (linkId != -1)
							bbMapper.routingTables[Integer.valueOf(bbMapper.nodes[tileId].getId())][srcTile][dstTile] = linkId;
					}
				}
			}
		}
		return true;
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
		case BranchAndBoundMapper.NORTH:
			row++;
			break;
		case BranchAndBoundMapper.SOUTH:
			row--;
			break;
		case BranchAndBoundMapper.EAST:
			column++;
			break;
		case BranchAndBoundMapper.WEST:
			column--;
			break;
		default:
			return -1;
		}
		int linkId;
		for (linkId = 0; linkId < bbMapper.links.length; linkId++) {
			if (Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[Integer.valueOf(bbMapper.links[linkId].getFirstNode())], ROW)) == origRow
					&& Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[Integer.valueOf(bbMapper.links[linkId].getFirstNode())], COLUMN)) == origColumn
					&& Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[Integer.valueOf(bbMapper.links[linkId].getSecondNode())], ROW)) == row
					&& Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[Integer.valueOf(bbMapper.links[linkId].getSecondNode())], COLUMN)) == column)
				break;
			if (Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[Integer.valueOf(bbMapper.links[linkId].getSecondNode())], ROW)) == origRow
					&& Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[Integer.valueOf(bbMapper.links[linkId].getSecondNode())], COLUMN)) == origColumn
					&& Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[Integer.valueOf(bbMapper.links[linkId].getFirstNode())], ROW)) == row
					&& Integer.valueOf(bbMapper.getNodeTopologyParameter(bbMapper.nodes[Integer.valueOf(bbMapper.links[linkId].getFirstNode())], COLUMN)) == column)
				break;
		}
		if (linkId == bbMapper.links.length) {
			logger.fatal("Error in locating link");
			System.exit(-1);
		}
		return linkId;
	}

	private void generateRoutingTable() {
		// reset all the BW_usage
		for (int i = 0; i < bbMapper.hSize; i++)
			for (int j = 0; j < bbMapper.nodes.length / bbMapper.hSize; j++)
				for (int k = 0; k < 4; k++)
					rSynLinkBandwidthUsage[i][j][k] = 0;

		routingTable = new int[bbMapper.hSize][bbMapper.nodes.length / bbMapper.hSize][bbMapper.nodes.length][bbMapper.nodes.length];
		for (int i = 0; i < bbMapper.hSize; i++) {
			for (int j = 0; j < bbMapper.nodes.length / bbMapper.hSize; j++) {
				for (int k = 0; k < bbMapper.nodes.length; k++) {
					for (int m = 0; m < bbMapper.nodes.length; m++)
						routingTable[i][j][k][m] = -2;
				}
			}
		}

		// if it's a real child mapping node.
		if (stage == bbMapper.cores.length)
			routeTraffics(0, bbMapper.cores.length - 1, true, true);
		// if it's the node which generate min upperBound
		else {
			for (int i = 0; i < stage; i++)
				routeTraffics(i, i, true, true);
			routeTraffics(stage, bbMapper.cores.length - 1, true, true);
		}
	}

}
