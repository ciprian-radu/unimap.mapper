package ro.ulbsibiu.acaps.mapper.bb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import ro.ulbsibiu.acaps.mapper.bb.BranchAndBoundMapper.RoutingEffort;

/**
 * Represents a node from the search tree of the Branch-and-Bound algorithm
 * 
 * @author cipi
 * 
 */
class MappingNode {

	/** the Branch-and-Bound mapper */
	private BranchAndBoundMapper bbMapper;

	/** It is an illegal node if it violates the spec constructor will init this */
	private boolean illegal;

	/** counter (it is basically the ID of the mapping node) */
	static int cnt;

	/** How many processes have been mapped */
	int stage;

	private int[] mappingSequency;

	private boolean[] tileOccupancyTable;

	float cost;

	float lowerBound;

	float upperBound;

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

	// working space
	private int[][][] rSynLinkBandwidthUsageTemp;

	/** [row][col][src_tile][dst_tile] */
	private int[][][][] routingTable;

	// The following three member are useful only in routing_synthesis mode and
	// the routing_effort is HARD

	/** 0: route in X; 1: route in Y */
	private int[] routingBitArray;

	/** The <tt>routingBitArray</tt> in integer form */
	private int routingInt;

	private int[] bestRoutingBitArray;

	private boolean firstRoutingPath;

	private int maxRoutingInt;

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
		assert bbMapper != null;
		this.bbMapper = bbMapper;

		illegal = false;

		tileOccupancyTable = null;
		mappingSequency = null;
		linkBandwidthUsage = null;
		rSynLinkBandwidthUsage = null;
		rSynLinkBandwidthUsageTemp = null;
		
		if (bbMapper.buildRoutingTable) {
			routingTable = new int[bbMapper.gEdgeSize][bbMapper.gEdgeSize][bbMapper.gTileNum][bbMapper.gTileNum];
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
		mappingSequency = new int[bbMapper.gProcNum];
		for (int i = 0; i < bbMapper.gProcNum; i++)
			mappingSequency[i] = -1;

		stage = parent.stage;

		int proc1 = bbMapper.procMapArray[stage];
		// if (bbMapper.gProcess[proc1]->is_locked() &&
		// bbMapper.gProcess[proc1]->lock_to !=
		// tileId) {
		// illegal = true;
		// return;
		// }

		lowerBound = parent.lowerBound;
		upperBound = parent.upperBound;

		// Copy the parent's partial mapping
		mappingSequency = Arrays.copyOf(parent.mappingSequency,
				bbMapper.gProcNum);

		if (bbMapper.buildRoutingTable) {
			// Copy the parent's link bandwidth usage
			rSynLinkBandwidthUsage = new int[bbMapper.gEdgeSize][bbMapper.gEdgeSize][4];
			for (int i = 0; i < bbMapper.gEdgeSize; i++) {
				for (int j = 0; j < bbMapper.gEdgeSize; j++) {
					rSynLinkBandwidthUsage[i][j] = Arrays.copyOf(
							parent.rSynLinkBandwidthUsage[i][j], 4);
				}
			}
		} else {
			linkBandwidthUsage = new int[bbMapper.gLinkNum];
			linkBandwidthUsage = Arrays.copyOf(parent.linkBandwidthUsage,
					bbMapper.gLinkNum);
		}

		// Map the next process to tile tileId
		mappingSequency[stage] = tileId;
		next = null;
		cost = parent.cost;

		for (int i = 0; i < stage; i++) {
			int tile1 = tileId;
			int tile2 = mappingSequency[i];
			float thisTranCost = bbMapper.procMatrix[i][stage];
			thisTranCost = thisTranCost * bbMapper.archMatrix[tile1][tile2];
			cost += thisTranCost;
			if (thisTranCost > BranchAndBoundMapper.MAX_PER_TRAN_COST) {
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
				int tile2 = mappingSequency[i];
				proc1 = bbMapper.procMapArray[stage];
				int proc2 = bbMapper.procMapArray[i];
				if (bbMapper.gProcess[proc1].getToBandwidthRequirement()[proc2] > 0) {
					for (int j = 0; j < bbMapper.linkUsageList[tile1][tile2]
							.size(); j++) {
						int linkId = bbMapper.linkUsageList[tile1][tile2]
								.get(j);
						linkBandwidthUsage[linkId] += bbMapper.gProcess[proc1]
								.getToBandwidthRequirement()[proc2];
						if (linkBandwidthUsage[linkId] > bbMapper.gLink[linkId]
								.getBandwidth()) {
							cost = BranchAndBoundMapper.MAX_VALUE + 1;
							illegal = true;
							return;
						}
					}
				}
				if (bbMapper.gProcess[proc1].getFromBandwidthRequirement()[proc2] > 0) {
					for (int j = 0; j < bbMapper.linkUsageList[tile2][tile1]
							.size(); j++) {
						int linkId = bbMapper.linkUsageList[tile2][tile1]
								.get(j);
						linkBandwidthUsage[linkId] += bbMapper.gProcess[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (linkBandwidthUsage[linkId] > bbMapper.gLink[linkId]
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

		if (!calcBound)
			return;

		tileOccupancyTable = new boolean[bbMapper.gTileNum];
		for (int i = 0; i < bbMapper.gTileNum; i++)
			tileOccupancyTable[i] = false;

		lowerBound = LowerBound();
		upperBound = UpperBound();
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
		assert bbMapper != null;
		this.bbMapper = bbMapper;

		illegal = false;
		tileOccupancyTable = null;
		mappingSequency = null;
		linkBandwidthUsage = null;
		rSynLinkBandwidthUsage = null;
		rSynLinkBandwidthUsageTemp = null;
		
		if (bbMapper.buildRoutingTable) {
			routingTable = new int[bbMapper.gEdgeSize][bbMapper.gEdgeSize][bbMapper.gTileNum][bbMapper.gTileNum];
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
		mappingSequency = new int[bbMapper.gProcNum];
		for (int i = 0; i < bbMapper.gProcNum; i++)
			mappingSequency[i] = -1;

		stage = 1;
		mappingSequency[0] = tileId;
		next = null;
		cost = 0;

		// int proc1 = proc_map_array[0];
		// if (bbMapper.gProcess[proc1]->is_locked() &&
		// bbMapper.gProcess[proc1]->lock_to !=
		// tileId) {
		// illegal = true;
		// return;
		// }

		tileOccupancyTable = new boolean[bbMapper.gTileNum];
		for (int i = 0; i < bbMapper.gTileNum; i++)
			tileOccupancyTable[i] = false;

		if (bbMapper.buildRoutingTable) {
			rSynLinkBandwidthUsage = new int[bbMapper.gEdgeSize][bbMapper.gEdgeSize][4];
			for (int i = 0; i < bbMapper.gEdgeSize; i++) {
				for (int j = 0; j < bbMapper.gEdgeSize; j++) {
					for (int k = 0; k < 4; k++) {
						rSynLinkBandwidthUsage[i][j][k] = 0;
					}
				}
			}
		} else {
			linkBandwidthUsage = new int[bbMapper.gLinkNum];
			for (int i = 0; i < bbMapper.gLinkNum; i++)
				linkBandwidthUsage[i] = 0;
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
		assert bbMapper != null;
		this.bbMapper = bbMapper;

		tileOccupancyTable = null;
		mappingSequency = null;
		linkBandwidthUsage = null;
		rSynLinkBandwidthUsage = null;
		rSynLinkBandwidthUsageTemp = null;
		
		if (bbMapper.buildRoutingTable) {
			routingTable = new int[bbMapper.gEdgeSize][bbMapper.gEdgeSize][bbMapper.gTileNum][bbMapper.gTileNum];
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
		mappingSequency = new int[bbMapper.gProcNum];
		for (int i = 0; i < bbMapper.gProcNum; i++)
			mappingSequency[i] = -1;
		stage = origin.stage;
		illegal = origin.illegal;

		// Copy the parent's partial mapping
		for (int i = 0; i < bbMapper.gProcNum; i++)
			mappingSequency[i] = origin.mappingSequency[i];

		if (bbMapper.buildRoutingTable) {
			// Copy the parent's link bandwidth usage
			rSynLinkBandwidthUsage = new int[bbMapper.gEdgeSize][bbMapper.gEdgeSize][4];
			for (int i = 0; i < bbMapper.gEdgeSize; i++) {
				for (int j = 0; j < bbMapper.gEdgeSize; j++) {
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
		for (int i = 0; i < bbMapper.gTileNum; i++)
			tileOccupancyTable[i] = false;
		for (int i = 0; i < stage; i++)
			tileOccupancyTable[mappingSequency[i]] = true;

		occupancyTableReady = true;
		lowerBound = cost;

		// The first part of the cost is the communication between those
		// nodes that have been mapped and those have not yet.
		// We assume that the unmapped node can occupy the unoccupied tile
		// which has the lowest cost to the occupied node
		for (int i = 0; i < stage; i++) {
			for (int j = stage; j < bbMapper.gProcNum; j++) {
				if (bbMapper.procMatrix[i][j] == 0)
					continue;
				else
					lowerBound += bbMapper.procMatrix[i][j]
							* lowestUnitCost(mappingSequency[i]);
			}
		}
		// Now add the cost of the communication among all the un-mapped nodes
		int vol = 0;
		for (int i = stage; i < bbMapper.gProcNum; i++) {
			for (int j = i + 1; j < bbMapper.gProcNum; j++)
				vol += bbMapper.procMatrix[i][j];
		}
		lowerBound += vol * lowestUnmappedUnitCost();
		return lowerBound;
	}

	/**
	 * This calculates the upper bound cost of the this partial mapping in the
	 * current mapping
	 */
	private float UpperBound() {
		if (!occupancyTableReady) {
			for (int i = 0; i < bbMapper.gTileNum; i++)
				tileOccupancyTable[i] = false;
			for (int i = 0; i < stage; i++)
				tileOccupancyTable[mappingSequency[i]] = true;
		}

		greedyMapping();
		upperBound = cost;

		illegalChildMapping = false;

		if (bbMapper.buildRoutingTable) {
			createBandwidthTempMemory();
			if (!routeTraffics(stage, bbMapper.gProcNum - 1, false, true)) {
				illegalChildMapping = true;
				upperBound = BranchAndBoundMapper.MAX_VALUE;
				return upperBound;
			}
		} else if (!fixedVerifyBandwidthUsage()) {
			illegalChildMapping = true;
			upperBound = BranchAndBoundMapper.MAX_VALUE;
			return upperBound;
		}

		for (int i = 0; i < stage; i++) {
			int tile1 = mappingSequency[i];
			for (int j = stage; j < bbMapper.gProcNum; j++) {
				int tile2 = mappingSequency[j];
				upperBound += bbMapper.procMatrix[i][j]
						* bbMapper.archMatrix[tile1][tile2];
			}
		}
		for (int i = stage; i < bbMapper.gProcNum; i++) {
			int tile1 = mappingSequency[i];
			for (int j = i + 1; j < bbMapper.gProcNum; j++) {
				int tile2 = mappingSequency[j];
				upperBound += bbMapper.procMatrix[i][j]
						* bbMapper.archMatrix[tile1][tile2];
			}
		}
		return upperBound;
	}

	private boolean fixedVerifyBandwidthUsage() {
		int[] linkBandwidthUsageTemp = new int[bbMapper.gLinkNum];
		linkBandwidthUsageTemp = Arrays.copyOf(linkBandwidthUsage,
				bbMapper.gLinkNum);

		for (int i = 0; i < stage; i++) {
			int tile1 = mappingSequency[i];
			int proc1 = bbMapper.procMapArray[i];
			for (int j = stage; j < bbMapper.gProcNum; j++) {
				int tile2 = mappingSequency[j];
				int proc2 = bbMapper.procMapArray[j];
				if (bbMapper.gProcess[proc1].getToBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < bbMapper.linkUsageList[tile1][tile2]
							.size(); k++) {
						int linkId = bbMapper.linkUsageList[tile1][tile2]
								.get(k);
						linkBandwidthUsageTemp[linkId] += bbMapper.gProcess[proc1]
								.getToBandwidthRequirement()[proc2];
						if (linkBandwidthUsageTemp[linkId] > bbMapper.gLink[linkId]
								.getBandwidth()) {
							return false;
						}
					}
				}

				if (bbMapper.gProcess[proc1].getFromBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < bbMapper.linkUsageList[tile2][tile1]
							.size(); k++) {
						int linkId = bbMapper.linkUsageList[tile2][tile1]
								.get(k);
						linkBandwidthUsageTemp[linkId] += bbMapper.gProcess[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (linkBandwidthUsageTemp[linkId] > bbMapper.gLink[linkId]
								.getBandwidth()) {
							return false;
						}
					}
				}
			}
		}
		for (int i = stage; i < bbMapper.gProcNum; i++) {
			int tile1 = mappingSequency[i];
			int proc1 = bbMapper.procMapArray[i];
			for (int j = i + 1; j < bbMapper.gProcNum; j++) {
				int tile2 = mappingSequency[j];
				int proc2 = bbMapper.procMapArray[j];
				if (bbMapper.gProcess[proc1].getToBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < bbMapper.linkUsageList[tile1][tile2]
							.size(); k++) {
						int linkId = bbMapper.linkUsageList[tile1][tile2]
								.get(k);
						linkBandwidthUsageTemp[linkId] += bbMapper.gProcess[proc1]
								.getToBandwidthRequirement()[proc2];
						if (linkBandwidthUsageTemp[linkId] > bbMapper.gLink[linkId]
								.getBandwidth()) {
							return false;
						}
					}
				}

				if (bbMapper.gProcess[proc1].getFromBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < bbMapper.linkUsageList[tile2][tile1]
							.size(); k++) {
						int linkId = bbMapper.linkUsageList[tile2][tile1]
								.get(k);
						linkBandwidthUsageTemp[linkId] += bbMapper.gProcess[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (linkBandwidthUsageTemp[linkId] > bbMapper.gLink[linkId]
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
		for (int i = 0; i < bbMapper.gTileNum; i++)
			tileOccupancyTable[i] = false;
		for (int i = 0; i < stage; i++)
			tileOccupancyTable[mappingSequency[i]] = true;

		int index = -1;
		for (int tileId = 0; tileId < bbMapper.gTileNum; tileId++) {
			if (tileOccupancyTable[tileId])
				continue;
			float additionalCost = 0;
			for (int i = 0; i < stage; i++) {
				int tile1 = tileId;
				int tile2 = mappingSequency[i];
				additionalCost += bbMapper.procMatrix[i][stage]
						* bbMapper.archMatrix[tile1][tile2];
				if (additionalCost >= minimal)
					break;
			}
			if (additionalCost < minimal)
				minimal = additionalCost;
			index = tileId;
		}
		return index;
	}

	int mapToTile(int i) {
		return mappingSequency[i];
	}

	/**
	 * @return the tile to be mapped for the next node with the criteria of the
	 *         greedy mapping of the current one.
	 */
	int bestUpperBoundCandidate() {
		return mappingSequency[stage];
	}

	/**
	 * @return the lowest cost of the tileId to any unoccupied tile.
	 */
	private float lowestUnitCost(int tileId) {
		float min = 50000;
		for (int i = 0; i < bbMapper.gTileNum; i++) {
			if (i == tileId)
				continue;
			if (tileOccupancyTable[i])
				continue;
			if (bbMapper.archMatrix[tileId][i] < min)
				min = bbMapper.archMatrix[tileId][i];
		}
		return min;
	}

	/**
	 * 
	 * @return the lowest cost between any two unoccupied tiles
	 */
	private float lowestUnmappedUnitCost() {
		float min = 50000;
		for (int i = 0; i < bbMapper.gTileNum; i++) {
			if (tileOccupancyTable[i])
				continue;
			for (int j = i + 1; j < bbMapper.gTileNum; j++) {
				if (tileOccupancyTable[j])
					continue;
				if (bbMapper.archMatrix[i][j] < min)
					min = bbMapper.archMatrix[i][j];
			}
		}
		return min;
	}

	/**
	 * Map the other unmapped process node using greedy mapping
	 */
	private void greedyMapping() {
		for (int i = stage; i < bbMapper.gProcNum; i++) {
			int sumRow = 0;
			int sumCol = 0;
			int vol = 0;
			for (int j = 0; j < i; j++) {
				if (bbMapper.procMatrix[i][j] == 0)
					continue;
				int tileId = mappingSequency[j];
				int row = tileId / bbMapper.gEdgeSize;
				int col = tileId % bbMapper.gEdgeSize;
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
		for (int i = 0; i < bbMapper.gTileNum; i++) {
			if (tileOccupancyTable[i])
				continue;
			if (goodRow < 0) {
				bestId = i;
				break;
			}
			int row = i / bbMapper.gEdgeSize;
			int col = i % bbMapper.gEdgeSize;
			float dist = Math.abs(goodRow - row) + Math.abs(goodCol - col);
			if (dist < minDist) {
				minDist = dist;
				bestId = i;
			}
		}
		mappingSequency[procId] = bestId;
		tileOccupancyTable[bestId] = true;
	}

	boolean isExpandable(int tileId) {
		// If it's an illegal mapping, then just return false
		for (int i = 0; i < stage; i++) {
			if (mappingSequency[i] == tileId) // the tile has already been
												// occupied
				return false;
		}
		return true;
	}

	int getStage() {
		return stage;
	}

	private void createBandwidthTempMemory() {
		// Copy the bandwidth usage status to rSynLinkBandwidthUsageTemp
		rSynLinkBandwidthUsageTemp = new int[bbMapper.gEdgeSize][bbMapper.gEdgeSize][4];
		for (int i = 0; i < bbMapper.gEdgeSize; i++) {
			for (int j = 0; j < bbMapper.gEdgeSize; j++) {
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
		for (int cur_stage = beginStage; cur_stage <= endStage; cur_stage++) {
			int new_proc = bbMapper.procMapArray[cur_stage];
			// Sort the request in the queue according to the BW
			// However, if the src and the dst are in the same row or in the
			// same column,
			// then we should insert it at the head of the queue.
			for (int i = 0; i < cur_stage; i++) {
				int old_proc = bbMapper.procMapArray[i];
				if (bbMapper.gProcess[new_proc].getToBandwidthRequirement()[old_proc] != 0) {
					ProcComm proc_comm = new ProcComm();
					proc_comm.srcProc = cur_stage; // we put virtual proc id
					proc_comm.dstProc = i;
					proc_comm.bandwidth = bbMapper.gProcess[new_proc]
							.getToBandwidthRequirement()[old_proc];
					proc_comm.adaptivity = calculateAdaptivity(
							mappingSequency[proc_comm.srcProc],
							mappingSequency[proc_comm.dstProc],
							proc_comm.bandwidth);
					for (Iterator<ProcComm> iterator = Q.iterator(); iterator
							.hasNext();) {
						ProcComm procComm = (ProcComm) iterator.next();
						if ((proc_comm.adaptivity < procComm.adaptivity)
								|| (proc_comm.adaptivity == procComm.adaptivity && proc_comm.bandwidth > procComm.bandwidth))
							break;
					}
					Q.add(proc_comm);
				}
				if (bbMapper.gProcess[new_proc].getFromBandwidthRequirement()[old_proc] > 0) {
					ProcComm proc_comm = new ProcComm();
					proc_comm.srcProc = i;
					proc_comm.dstProc = cur_stage;
					proc_comm.bandwidth = bbMapper.gProcess[new_proc]
							.getFromBandwidthRequirement()[old_proc];
					proc_comm.adaptivity = calculateAdaptivity(
							mappingSequency[proc_comm.srcProc],
							mappingSequency[proc_comm.dstProc],
							proc_comm.bandwidth);
					for (Iterator<ProcComm> iterator = Q.iterator(); iterator
							.hasNext();) {
						ProcComm procComm = (ProcComm) iterator.next();
						if ((proc_comm.adaptivity < procComm.adaptivity)
								|| (proc_comm.adaptivity == procComm.adaptivity && proc_comm.bandwidth > procComm.bandwidth))
							break;
					}
					Q.add(proc_comm);
				}
			}
		}
		// now route the traffic
		for (int i = 0; i < Q.size(); i++) {
			int src_proc = Q.get(i).srcProc;
			int dst_proc = Q.get(i).dstProc;
			int src_tile = mappingSequency[src_proc];
			int dst_tile = mappingSequency[dst_proc];
			int BW = Q.get(i).bandwidth;
			if (RoutingEffort.EASY.equals(bbMapper.routingEffort)) {
				if (!routeTrafficEasy(src_tile, dst_tile, BW, commit,
						updateRoutingTable))
					return false;
			} else {
				if (!routeTrafficHard(src_tile, dst_tile, BW, commit,
						updateRoutingTable))
					return false;
			}
		}
		return true;
	}

	/**
	 * currently there is only two levels of adaptivity. 0 for no adaptivity, 1
	 * for (maybe) some adaptivity
	 * 
	 * @param srcTile
	 * @param dstTile
	 * @param bandwidth
	 * @return
	 */
	private final int calculateAdaptivity(int srcTile, int dstTile,
			int bandwidth) {
		int adaptivity;
		if (bbMapper.gTile[srcTile].getRow() == bbMapper.gTile[dstTile]
				.getRow()
				|| bbMapper.gTile[srcTile].getColumn() == bbMapper.gTile[dstTile]
						.getColumn()) {
			adaptivity = 0;
			return adaptivity;
		}

		int[][][] bandwidthUusage = rSynLinkBandwidthUsage;

		adaptivity = 1;
		int row = bbMapper.gTile[srcTile].getRow();
		int col = bbMapper.gTile[srcTile].getColumn();
		int direction = -2;
		while (row != bbMapper.gTile[dstTile].getRow()
				|| col != bbMapper.gTile[dstTile].getColumn()) {
			// For west-first routing
			if (BranchAndBoundMapper.LegalTurnSet.WEST_FIRST
					.equals(bbMapper.legalTurnSet)) {
				if (col > bbMapper.gTile[dstTile].getColumn()) // step west
					return 0;
				else if (col == bbMapper.gTile[dstTile].getColumn())
					return 0;
				else if (row == bbMapper.gTile[dstTile].getRow())
					return 0;
				// Here comes the flexibility. We can choose whether to go
				// vertical or horizontal
				else {
					int direction1 = (row < bbMapper.gTile[dstTile].getRow()) ? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
					if (bandwidthUusage[row][col][direction1] + bandwidth < bbMapper.linkBandwidth
							&& bandwidthUusage[row][col][BranchAndBoundMapper.EAST]
									+ bandwidth < bbMapper.linkBandwidth)
						return 1;
					direction = (bandwidthUusage[row][col][direction1] < bandwidthUusage[row][col][BranchAndBoundMapper.EAST]) ? direction1
							: BranchAndBoundMapper.EAST;
				}
			}
			// For odd-even routing
			else if (BranchAndBoundMapper.LegalTurnSet.ODD_EVEN
					.equals(bbMapper.legalTurnSet)) {
				int e0 = bbMapper.gTile[dstTile].getColumn() - col;
				int e1 = bbMapper.gTile[dstTile].getRow() - row;
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
									|| col == bbMapper.gTile[srcTile]
											.getColumn())
								direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
										: BranchAndBoundMapper.SOUTH;
							if (bbMapper.gTile[dstTile].getColumn() % 2 == 1
									|| e0 != 1)
								direction2 = BranchAndBoundMapper.EAST;
							if (direction1 == -1)
								direction = direction2;
							else if (direction2 == -1)
								direction = direction1;
							else {// we have two choices
								if (bandwidthUusage[row][col][direction1]
										+ bandwidth < bbMapper.linkBandwidth
										&& bandwidthUusage[row][col][direction2]
												+ bandwidth < bbMapper.linkBandwidth)
									return 1;
								direction = (bandwidthUusage[row][col][direction1] < bandwidthUusage[row][col][direction2]) ? direction1
										: direction2;
							}
						}
					} else { // westbound messages
						if (col % 2 != 0 || e1 == 0)
							direction = BranchAndBoundMapper.WEST;
						else {
							int direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
									: BranchAndBoundMapper.SOUTH;
							if (bandwidthUusage[row][col][direction1]
									+ bandwidth < bbMapper.linkBandwidth
									&& bandwidthUusage[row][col][BranchAndBoundMapper.WEST]
											+ bandwidth < bbMapper.linkBandwidth)
								return 1;
							direction = (bandwidthUusage[row][col][BranchAndBoundMapper.WEST] < bandwidthUusage[row][col][direction1]) ? BranchAndBoundMapper.WEST
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
				System.err.println("Error");
				;
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
	 * @param dstTile
	 * @param bandwidth
	 * @param commit
	 * @param updateRoutingTable
	 * @return
	 */
	private boolean routeTrafficEasy(int srcTile, int dstTile, int bandwidth,
			boolean commit, boolean updateRoutingTable) {
		int row = bbMapper.gTile[srcTile].getRow();
		int col = bbMapper.gTile[srcTile].getColumn();

		int[][][] bandwidthUsage = commit ? rSynLinkBandwidthUsage
				: rSynLinkBandwidthUsageTemp;
		int direction = -2;
		while (row != bbMapper.gTile[dstTile].getRow()
				|| col != bbMapper.gTile[dstTile].getRow()) {
			// For west-first routing
			if (BranchAndBoundMapper.LegalTurnSet.WEST_FIRST
					.equals(bbMapper.legalTurnSet)) {
				if (col > bbMapper.gTile[dstTile].getColumn()) // step west
					direction = BranchAndBoundMapper.WEST;
				else if (col == bbMapper.gTile[dstTile].getColumn())
					direction = (row < bbMapper.gTile[dstTile].getRow()) ? BranchAndBoundMapper.NORTH
							: BranchAndBoundMapper.SOUTH;
				else if (row == bbMapper.gTile[dstTile].getRow())
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
						direction = (row < bbMapper.gTile[dstTile].getRow()) ? BranchAndBoundMapper.NORTH
								: BranchAndBoundMapper.SOUTH;
				}
			}
			// For odd-even routing
			else if (BranchAndBoundMapper.LegalTurnSet.ODD_EVEN
					.equals(bbMapper.legalTurnSet)) {
				int e0 = bbMapper.gTile[dstTile].getColumn() - col;
				int e1 = bbMapper.gTile[dstTile].getRow() - row;
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
									|| col == bbMapper.gTile[srcTile]
											.getColumn())
								direction1 = (e1 > 0) ? BranchAndBoundMapper.NORTH
										: BranchAndBoundMapper.SOUTH;
							if (bbMapper.gTile[dstTile].getColumn() % 2 == 1
									|| e0 != 1)
								direction2 = BranchAndBoundMapper.EAST;
							if (direction1 == -1 && direction2 == -1) {
								System.err.println("Error");
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
				System.err.println("Error");
				break;
			}
		}
		return true;
	}

	/**********************************************************************
	 * Route the traffic from src_tile to dst_tile using BW in complex * model,
	 * by which it means select the path from all its candidate * pathes which
	 * has the minimal maximal BW usage :) *
	 **********************************************************************/
	/**
	 * Route the traffic from srcTile to dstTile using bandwidth in complex
	 * model, by which it means select the path from all its candidate paths
	 * which has the minimal maximal bandwidth usage.
	 * 
	 * @param srcTile
	 * @param dstTile
	 * @param bandwidth
	 * @param commit
	 * @param updateRoutingTable
	 * @return
	 */
	private boolean routeTrafficHard(int srcTile, int dstTile, int bandwidth,
			boolean commit, boolean updateRoutingTable) {
		int row = bbMapper.gTile[srcTile].getRow();
		int col = bbMapper.gTile[srcTile].getColumn();

		int[][][] BW_usage = commit ? rSynLinkBandwidthUsage
				: rSynLinkBandwidthUsageTemp;

		// We can arrive at any destination with 2*bbMapper.gEdgeSize hops
		if (routingBitArray == null) {
			routingBitArray = new int[2 * bbMapper.gEdgeSize];
		}
		if (bestRoutingBitArray == null) {
			bestRoutingBitArray = new int[2 * bbMapper.gEdgeSize];
		}

		// In the following, we find the routing path which has the minimal
		// maximal
		// link BW usage and store that routing path to best_routing_bit_array
		int min_path_BW = Integer.MAX_VALUE;
		int x_hop = bbMapper.gTile[srcTile].getColumn()
				- bbMapper.gTile[dstTile].getColumn();
		x_hop = (x_hop >= 0) ? x_hop : (0 - x_hop);
		int y_hop = bbMapper.gTile[srcTile].getRow()
				- bbMapper.gTile[dstTile].getRow();
		y_hop = (y_hop >= 0) ? y_hop : (0 - y_hop);

		initRoutingPathGenerator(x_hop, y_hop);

		while (nextRoutingPath(x_hop, y_hop)) { // For each path
			int usage = pathBandwidthUsage(row, col,
					bbMapper.gTile[dstTile].getRow(),
					bbMapper.gTile[dstTile].getColumn(), BW_usage, bandwidth);
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
		while (row != bbMapper.gTile[dstTile].getRow()
				|| col != bbMapper.gTile[dstTile].getColumn()) {

			if (bestRoutingBitArray[hop_id++] != 0)
				direction = (row < bbMapper.gTile[dstTile].getRow()) ? BranchAndBoundMapper.NORTH
						: BranchAndBoundMapper.SOUTH;
			else
				direction = (col < bbMapper.gTile[dstTile].getColumn()) ? BranchAndBoundMapper.EAST
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
				System.err.println("Error");
				break;
			}
		}
		return true;
	}

	/**
	 * Fulfills two tasks. First, check to see whether it's a valid path
	 * according to the selected routing algorithm. Second, it checks to see if
	 * any bandwidth requirement violates. If either of the two conditions is
	 * not met, return INT_MAX.
	 * 
	 * @param srcRow
	 * @param srcColumn
	 * @param dstRow
	 * @param dstColumn
	 * @param bandwidthUsage
	 * @param bandwidth
	 * @return
	 */
	private int pathBandwidthUsage(int srcRow, int srcColumn, int dstRow,
			int dstColumn, int[][][] bandwidthUsage, int bandwidth) {
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
							assert (!(direction1 == -1 && direction2 == -1));
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
				System.err.println("Error");
				break;
			}
			hop_id++;

		}
		return 1;
	}

	/**
	 * @param xHop
	 * @param yHop
	 * @return
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
	 * @param yHop
	 * @return
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

		/**********************************************************************
		 * find the next routing path based on the current routing_bit_array *
		 * the next one is the one which is the minimal array which is larger *
		 * than the current routing_bit_array but with the same number of 1s *
		 **********************************************************************/
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
		routingBitArray = new int[2 * bbMapper.gEdgeSize];
		Arrays.fill(routingBitArray, 0);
		int index = 0;
		int cur_one_bits = 0;
		while (r != 0) {
			routingBitArray[index] = r & 1;
			if (routingBitArray[index] != 0)
				cur_one_bits++;
			if (cur_one_bits > onebits)
				return false;
			index++;
			r = r >> 1;
		}
		if (cur_one_bits == onebits)
			return true;
		else
			return false;
	}

	boolean programRouters() {
		generateRoutingTable();
		// clean all the old routing table
		for (int tile_id = 0; tile_id < bbMapper.gTileNum; tile_id++) {
			for (int src_tile = 0; src_tile < bbMapper.gTileNum; src_tile++) {
				for (int dst_tile = 0; dst_tile < bbMapper.gTileNum; dst_tile++) {
					if (tile_id == dst_tile)
						bbMapper.gTile[tile_id].setRoutingEntry(src_tile,
								dst_tile, -1);
					else
						bbMapper.gTile[tile_id].setRoutingEntry(src_tile,
								dst_tile, -2);
				}
			}
		}

		for (int row = 0; row < bbMapper.gEdgeSize; row++) {
			for (int col = 0; col < bbMapper.gEdgeSize; col++) {
				int tile_id = row * bbMapper.gEdgeSize + col;
				for (int src_tile = 0; src_tile < bbMapper.gTileNum; src_tile++) {
					for (int dst_tile = 0; dst_tile < bbMapper.gTileNum; dst_tile++) {
						int link_id = locateLink(row, col,
								routingTable[row][col][src_tile][dst_tile]);
						if (link_id != -1)
							bbMapper.gTile[tile_id].setRoutingEntry(src_tile,
									dst_tile, link_id);
					}
				}
			}
		}
		return true;
	}

	/**
	 * find out the link ID. Ff the direction is not set, return -1
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
		int link_id;
		for (link_id = 0; link_id < bbMapper.gLinkNum; link_id++) {
			if (bbMapper.gTile[bbMapper.gLink[link_id].getFromTileId()]
					.getRow() == origRow
					&& bbMapper.gTile[bbMapper.gLink[link_id].getFromTileId()]
							.getColumn() == origColumn
					&& bbMapper.gTile[bbMapper.gLink[link_id].getToTileId()]
							.getRow() == row
					&& bbMapper.gTile[bbMapper.gLink[link_id].getToTileId()]
							.getColumn() == column)
				break;
		}
		if (link_id == bbMapper.gLinkNum) {
			System.err.println("Error in locating link");
			System.exit(-1);
		}
		return link_id;
	}

	private void generateRoutingTable() {
		// reset all the BW_usage
		for (int i = 0; i < bbMapper.gEdgeSize; i++)
			for (int j = 0; j < bbMapper.gEdgeSize; j++)
				for (int k = 0; k < 4; k++)
					rSynLinkBandwidthUsage[i][j][k] = 0;

		routingTable = new int[bbMapper.gEdgeSize][bbMapper.gEdgeSize][bbMapper.gTileNum][bbMapper.gTileNum];
		for (int i = 0; i < bbMapper.gEdgeSize; i++) {
			for (int j = 0; j < bbMapper.gEdgeSize; j++) {
				for (int k = 0; k < bbMapper.gTileNum; k++) {
					for (int m = 0; m < bbMapper.gTileNum; m++)
						routingTable[i][j][k][m] = -2;
				}
			}
		}

		// if it's a real child mappingnode.
		if (stage == bbMapper.gProcNum)
			routeTraffics(0, bbMapper.gProcNum - 1, true, true);
		// if it's the node which generate min upperBound
		else {
			for (int i = 0; i < stage; i++)
				routeTraffics(i, i, true, true);
			routeTraffics(stage, bbMapper.gProcNum - 1, true, true);
		}
	}

}
