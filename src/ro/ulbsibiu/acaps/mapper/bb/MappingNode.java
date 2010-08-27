package ro.ulbsibiu.acaps.mapper.bb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import ro.ulbsibiu.acaps.mapper.bb.BranchAndBoundMapper.RoutingEffort;
import ro.ulbsibiu.acaps.mapper.sa.Link;
import ro.ulbsibiu.acaps.mapper.sa.Process;
import ro.ulbsibiu.acaps.mapper.sa.Tile;

class MappingNode {

	private static final int NORTH = 0;

	private static final int SOUTH = 1;

	private static final int EAST = 2;

	private static final int WEST = 3;

	// FIXME init these fields through constructor

	/** the number of tiles (nodes) from the NoC */
	private int gTileNum;

	/**
	 * the size of the 2D mesh, sqrt(gTileNum) (sqrt(gTileNum) * sqrt(gTileNum)
	 * = gTileNum)
	 */
	private int gEdgeSize;

	/**
	 * the number of processes (tasks). Note that each core has only one task
	 * associated to it.
	 */
	private int gProcNum;

	/**
	 * the number of links from the NoC
	 */
	private int gLinkNum;

	/** the tiles from the Network-on-Chip (NoC) */
	private Tile[] gTile;

	/** the processes (tasks, cores) */
	private Process[] gProcess;

	/** the communication channels from the NoC */
	private Link[] gLink;

	/**
	 * what links are used by tiles to communicate (each source - destination
	 * tile pair has a list of link IDs). The matrix must have size
	 * <tt>gTileNum x gTileNum</tt>. <b>This must be <tt>null</tt> when
	 * <tt>buildRoutingTable</tt> is <tt>true</tt> </b>
	 */
	private List<Integer>[][] linkUsageList = null;

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

	private RoutingEffort routingEffort;

	private int linkBandwidth;

	private int[][] procMatrix = null;

	private float[][] archMatrix = null;

	// proc_map_array[i] represents the actual process that the i-th mapped
	// process
	// corresponding to
	private int[] proc_map_array = null;

	// It is an illegal node if it violates the spec constructor will init this
	private boolean illegal;

	static int cnt;

	// How many processes have been mapped
	int stage;

	private int[] mappingSequency;

	private boolean[] tileOccupancyTable;

	double cost;

	double lowerBound;

	double upperBound;

	private boolean occupancyTableReady;

	private boolean illegal_child_mapping;

	// The array record how much BW have been used for each link, used for
	// xy-routing
	private int[] link_BW_usage;

	// this array is used when we also need to synthesize the routing table This
	// can be indexed by [src_row][src_col][direction]
	private int[][][] R_syn_link_BW_usage;

	// working space
	private int[][][] R_syn_link_BW_usage_temp;

	// [row][col][src_tile][dst_tile]
	private int[][][][] routing_table;

	/**********************************************************************
	 * The following three member are useful only in routing_synthesis * mode
	 * and the routing_effort is HARD *
	 **********************************************************************/
	// 0: route in X; 1: route in Y
	private int[] routing_bit_array;

	// The routing_bit_array in integer form
	private int routing_int;

	private int[] best_routing_bit_array;

	private boolean first_routing_path;

	private int MAX_ROUTING_INT;

	MappingNode next;

	public MappingNode(final MappingNode parent, int tileId, boolean calcBound) {
		illegal = false;

		tileOccupancyTable = null;
		mappingSequency = null;
		link_BW_usage = null;
		R_syn_link_BW_usage = null;
		R_syn_link_BW_usage_temp = null;
		routing_table = null;

		routing_bit_array = null;
		best_routing_bit_array = null;

		occupancyTableReady = false;
		lowerBound = -1;
		cnt++;
		mappingSequency = new int[gProcNum];
		for (int i = 0; i < gProcNum; i++)
			mappingSequency[i] = -1;

		stage = parent.stage;

		int proc1 = proc_map_array[stage];
		// if (gProcess[proc1]->is_locked() && gProcess[proc1]->lock_to !=
		// tileId) {
		// illegal = true;
		// return;
		// }

		lowerBound = parent.lowerBound;
		upperBound = parent.upperBound;

		// Copy the parent's partial mapping
		mappingSequency = Arrays.copyOf(parent.mappingSequency, gProcNum);

		if (buildRoutingTable) {
			// Copy the parent's link bandwidth usage
			R_syn_link_BW_usage = new int[gEdgeSize][gEdgeSize][4];
			for (int i = 0; i < gEdgeSize; i++) {
				for (int j = 0; j < gEdgeSize; j++) {
					R_syn_link_BW_usage[i][j] = Arrays.copyOf(
							parent.R_syn_link_BW_usage[i][j], 4);
				}
			}
		} else {
			link_BW_usage = new int[gLinkNum];
			link_BW_usage = Arrays.copyOf(parent.link_BW_usage, gLinkNum);
		}

		// Map the next process to tile tileId
		mappingSequency[stage] = tileId;
		next = null;
		cost = parent.cost;

		for (int i = 0; i < stage; i++) {
			int tile1 = tileId;
			int tile2 = mappingSequency[i];
			float this_tran_cost = procMatrix[i][stage];
			this_tran_cost = this_tran_cost * archMatrix[tile1][tile2];
			cost += this_tran_cost;
			if (this_tran_cost > BranchAndBoundMapper.MAX_PER_TRAN_COST) {
				illegal = true;
				return;
			}
		}

		if (buildRoutingTable) {
			if (!route_traffics(stage, stage, false, false)) { // FIXME are the
																// last 2 params
																// false?
				cost = BranchAndBoundMapper.MAX_VALUE + 1;
				illegal = true;
				return;
			}
		} else {
			for (int i = 0; i < stage; i++) {
				int tile1 = tileId;
				int tile2 = mappingSequency[i];
				proc1 = proc_map_array[stage];
				int proc2 = proc_map_array[i];
				if (gProcess[proc1].getToBandwidthRequirement()[proc2] > 0) {
					for (int j = 0; i < linkUsageList[tile1][tile2].size(); j++) {
						int link_id = linkUsageList[tile1][tile2].get(j);
						link_BW_usage[link_id] += gProcess[proc1]
								.getToBandwidthRequirement()[proc2];
						if (link_BW_usage[link_id] > gLink[link_id]
								.getBandwidth()) {
							cost = BranchAndBoundMapper.MAX_VALUE + 1;
							illegal = true;
							return;
						}
					}
				}
				if (gProcess[proc1].getFromBandwidthRequirement()[proc2] > 0) {
					for (int j = 0; i < linkUsageList[tile2][tile1].size(); j++) {
						int link_id = linkUsageList[tile2][tile1].get(j);
						link_BW_usage[link_id] += gProcess[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (link_BW_usage[link_id] > gLink[link_id]
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

		tileOccupancyTable = new boolean[gTileNum];
		for (int i = 0; i < gTileNum; i++)
			tileOccupancyTable[i] = false;

		lowerBound = LowerBound();
		upperBound = UpperBound();
	}

	MappingNode(int tileId) {
		illegal = false;
		tileOccupancyTable = null;
		mappingSequency = null;
		link_BW_usage = null;
		R_syn_link_BW_usage = null;
		R_syn_link_BW_usage_temp = null;
		routing_table = null;
		occupancyTableReady = false;
		lowerBound = -1;

		routing_bit_array = null;
		best_routing_bit_array = null;

		cnt++;
		mappingSequency = new int[gProcNum];
		for (int i = 0; i < gProcNum; i++)
			mappingSequency[i] = -1;

		stage = 1;
		mappingSequency[0] = tileId;
		next = null;
		cost = 0;

		int proc1 = proc_map_array[0];
		// if (gProcess[proc1]->is_locked() && gProcess[proc1]->lock_to !=
		// tileId) {
		// illegal = true;
		// return;
		// }

		tileOccupancyTable = new boolean[gTileNum];
		for (int i = 0; i < gTileNum; i++)
			tileOccupancyTable[i] = false;

		if (buildRoutingTable) {
			R_syn_link_BW_usage = new int[gEdgeSize][gEdgeSize][4];
			for (int i = 0; i < gEdgeSize; i++) {
				for (int j = 0; j < gEdgeSize; j++) {
					for (int k = 0; k < 4; k++) {
						R_syn_link_BW_usage[i][j][k] = 0;
					}
				}
			}
		} else {
			link_BW_usage = new int[gLinkNum];
			for (int i = 0; i < gLinkNum; i++)
				link_BW_usage[i] = 0;
		}

		lowerBound = LowerBound();
		upperBound = UpperBound();
	}

	// essentially, this is to generate a mapping node which is a copy of
	// the node origin
	MappingNode(final MappingNode origin) {
		tileOccupancyTable = null;
		mappingSequency = null;
		link_BW_usage = null;
		R_syn_link_BW_usage = null;
		R_syn_link_BW_usage_temp = null;
		routing_table = null;

		routing_bit_array = null;
		best_routing_bit_array = null;

		occupancyTableReady = false;
		lowerBound = -1;
		cnt++;
		mappingSequency = new int[gProcNum];
		for (int i = 0; i < gProcNum; i++)
			mappingSequency[i] = -1;
		stage = origin.stage;
		illegal = origin.illegal;

		// Copy the parent's partial mapping
		for (int i = 0; i < gProcNum; i++)
			mappingSequency[i] = origin.mappingSequency[i];

		if (buildRoutingTable) {
			// Copy the parent's link bandwidth usage
			R_syn_link_BW_usage = new int[gEdgeSize][gEdgeSize][4];
			for (int i = 0; i < gEdgeSize; i++) {
				for (int j = 0; j < gEdgeSize; j++) {
					R_syn_link_BW_usage[i][j] = Arrays.copyOf(
							origin.R_syn_link_BW_usage[i][j], 4);
				}
			}
		}
	}

	// This calculate the lower bound cost of the unmapped process nodes
	// in the current mapping
	private double LowerBound() {
		for (int i = 0; i < gTileNum; i++)
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
			for (int j = stage; j < gProcNum; j++) {
				if (procMatrix[i][j] == 0)
					continue;
				else
					lowerBound += procMatrix[i][j]
							* lowestUnitCost(mappingSequency[i]);
			}
		}
		// Now add the cost of the communication among all the un-mapped nodes
		int vol = 0;
		for (int i = stage; i < gProcNum; i++) {
			for (int j = i + 1; j < gProcNum; j++)
				vol += procMatrix[i][j];
		}
		lowerBound += vol * lowestUnmappedUnitCost();
		return lowerBound;
	}

	// This calculate the upper bound cost of the this partial mapping
	// in the current mapping
	private double UpperBound() {
		if (!occupancyTableReady) {
			for (int i = 0; i < gTileNum; i++)
				tileOccupancyTable[i] = false;
			for (int i = 0; i < stage; i++)
				tileOccupancyTable[mappingSequency[i]] = true;
		}

		GreedyMapping();
		upperBound = cost;

		illegal_child_mapping = false;

		if (buildRoutingTable) {
			create_BW_temp_memory();
			if (!route_traffics(stage, gProcNum - 1, false, false)) {// FIXME is
																		// the
																		// last
																		// param
																		// false?
				illegal_child_mapping = true;
				upperBound = BranchAndBoundMapper.MAX_VALUE;
				return upperBound;
			}
		} else if (!fixed_verify_BW_usage()) {
			illegal_child_mapping = true;
			upperBound = BranchAndBoundMapper.MAX_VALUE;
			return upperBound;
		}

		for (int i = 0; i < stage; i++) {
			int tile1 = mappingSequency[i];
			for (int j = stage; j < gProcNum; j++) {
				int tile2 = mappingSequency[j];
				upperBound += procMatrix[i][j] * archMatrix[tile1][tile2];
			}
		}
		for (int i = stage; i < gProcNum; i++) {
			int tile1 = mappingSequency[i];
			for (int j = i + 1; j < gProcNum; j++) {
				int tile2 = mappingSequency[j];
				upperBound += procMatrix[i][j] * archMatrix[tile1][tile2];
			}
		}
		return upperBound;
	}

	private boolean fixed_verify_BW_usage() {
		int[] link_BW_usage_temp = new int[gLinkNum];
		link_BW_usage_temp = Arrays.copyOf(link_BW_usage, gLinkNum);

		for (int i = 0; i < stage; i++) {
			int tile1 = mappingSequency[i];
			int proc1 = proc_map_array[i];
			for (int j = stage; j < gProcNum; j++) {
				int tile2 = mappingSequency[j];
				int proc2 = proc_map_array[j];
				if (gProcess[proc1].getToBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < linkUsageList[tile1][tile2].size(); k++) {
						int link_id = linkUsageList[tile1][tile2].get(k);
						link_BW_usage_temp[link_id] += gProcess[proc1]
								.getToBandwidthRequirement()[proc2];
						if (link_BW_usage_temp[link_id] > gLink[link_id]
								.getBandwidth()) {
							return false;
						}
					}
				}

				if (gProcess[proc1].getFromBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < linkUsageList[tile2][tile1].size(); k++) {
						int link_id = linkUsageList[tile2][tile1].get(k);
						link_BW_usage_temp[link_id] += gProcess[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (link_BW_usage_temp[link_id] > gLink[link_id]
								.getBandwidth()) {
							return false;
						}
					}
				}
			}
		}
		for (int i = stage; i < gProcNum; i++) {
			int tile1 = mappingSequency[i];
			int proc1 = proc_map_array[i];
			for (int j = i + 1; j < gProcNum; j++) {
				int tile2 = mappingSequency[j];
				int proc2 = proc_map_array[j];
				if (gProcess[proc1].getToBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; k < linkUsageList[tile1][tile2].size(); k++) {
						int link_id = linkUsageList[tile1][tile2].get(k);
						link_BW_usage_temp[link_id] += gProcess[proc1]
								.getToBandwidthRequirement()[proc2];
						if (link_BW_usage_temp[link_id] > gLink[link_id]
								.getBandwidth()) {
							return false;
						}
					}
				}

				if (gProcess[proc1].getFromBandwidthRequirement()[proc2] != 0) {
					for (int k = 0; i < linkUsageList[tile2][tile1].size(); k++) {
						int link_id = linkUsageList[tile2][tile1].get(k);
						link_BW_usage_temp[link_id] += gProcess[proc1]
								.getFromBandwidthRequirement()[proc2];
						if (link_BW_usage_temp[link_id] > gLink[link_id]
								.getBandwidth()) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	// This method returns the tile to be mapped for the next node which will
	// lead to the smallest partial mapping cost
	int bestCostCandidate() {
		double minimal = BranchAndBoundMapper.MAX_VALUE;
		for (int i = 0; i < gTileNum; i++)
			tileOccupancyTable[i] = false;
		for (int i = 0; i < stage; i++)
			tileOccupancyTable[mappingSequency[i]] = true;

		int index = -1;
		for (int tileId = 0; tileId < gTileNum; tileId++) {
			if (tileOccupancyTable[tileId])
				continue;
			float additionalCost = 0;
			for (int i = 0; i < stage; i++) {
				int tile1 = tileId;
				int tile2 = mappingSequency[i];
				additionalCost += procMatrix[i][stage]
						* archMatrix[tile1][tile2];
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

	// This method returns the tile to be mapped for the next node with the
	// criteria of the greedy mapping of the current one.
	int bestUpperBoundCandidate() {
		return mappingSequency[stage];
	}

	// This function returns the lowest cost of the tileId to any unoccupied
	// tile.
	private float lowestUnitCost(int tileId) {
		float min = 50000;
		for (int i = 0; i < gTileNum; i++) {
			if (i == tileId)
				continue;
			if (tileOccupancyTable[i])
				continue;
			if (archMatrix[tileId][i] < min)
				min = archMatrix[tileId][i];
		}
		return min;
	}

	// This function returns the lowest cost between anytwo unoccupied tiles
	private float lowestUnmappedUnitCost() {
		float min = 50000;
		for (int i = 0; i < gTileNum; i++) {
			if (tileOccupancyTable[i])
				continue;
			for (int j = i + 1; j < gTileNum; j++) {
				if (tileOccupancyTable[j])
					continue;
				if (archMatrix[i][j] < min)
					min = archMatrix[i][j];
			}
		}
		return min;
	}

	// Map the other unmapped process node using greedy mapping
	private void GreedyMapping() {
		for (int i = stage; i < gProcNum; i++) {
			int sumRow = 0;
			int sumCol = 0;
			int vol = 0;
			for (int j = 0; j < i; j++) {
				if (procMatrix[i][j] == 0)
					continue;
				int tileId = mappingSequency[j];
				int row = tileId / gEdgeSize;
				int col = tileId % gEdgeSize;
				sumRow += procMatrix[i][j] * row;
				sumCol += procMatrix[i][j] * col;
				vol += procMatrix[i][j];
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
			MapNode(i, myRow, myCol);
		}
	}

	// Try to map the node to an unoccupied tile which is cloest to
	// the tile(goodRow, goodCol)
	private void MapNode(int procId, float goodRow, float goodCol) {
		float minDist = 10000;
		int bestId = -1;
		for (int i = 0; i < gTileNum; i++) {
			if (tileOccupancyTable[i])
				continue;
			if (goodRow < 0) {
				bestId = i;
				break;
			}
			int row = i / gEdgeSize;
			int col = i % gEdgeSize;
			float dist = Math.abs(goodRow - row) + Math.abs(goodCol - col);
			if (dist < minDist) {
				minDist = dist;
				bestId = i;
			}
		}
		mappingSequency[procId] = bestId;
		tileOccupancyTable[bestId] = true;
	}

	boolean Expandable(int tileId) {
		// If it's an illegal mapping, then just return false
		for (int i = 0; i < stage; i++) {
			if (mappingSequency[i] == tileId) // the tile has already been
												// occupied
				return false;
		}
		return true;
	}

	int Stage() {
		return stage;
	}

	private void create_BW_temp_memory() {
		// Copy the bandwidth usage status to R_syn_link_BW_usage_temp
		R_syn_link_BW_usage_temp = new int[gEdgeSize][gEdgeSize][4];
		for (int i = 0; i < gEdgeSize; i++) {
			for (int j = 0; j < gEdgeSize; j++) {
				R_syn_link_BW_usage_temp[i][j] = Arrays.copyOf(
						R_syn_link_BW_usage[i][j], 4);
			}
		}
	}

	// fixing the routing tables of the tiles which are occupied by the
	// processes
	// from begin_stage to end_stage
	private boolean route_traffics(int begin_stage, int end_stage,
			boolean commit, boolean update_routing_table) {
		List<ProcComm> Q = new ArrayList<ProcComm>();
		for (int cur_stage = begin_stage; cur_stage <= end_stage; cur_stage++) {
			int new_proc = proc_map_array[cur_stage];
			// Sort the request in the queue according to the BW
			// However, if the src and the dst are in the same row or in the
			// same column,
			// then we should insert it at the head of the queue.
			for (int i = 0; i < cur_stage; i++) {
				int old_proc = proc_map_array[i];
				if (gProcess[new_proc].getToBandwidthRequirement()[old_proc] != 0) {
					ProcComm proc_comm = new ProcComm();
					proc_comm.src_proc = cur_stage; // we put virtual proc id
					proc_comm.dst_proc = i;
					proc_comm.BW = gProcess[new_proc]
							.getToBandwidthRequirement()[old_proc];
					proc_comm.adaptivity = calc_adaptivity(
							mappingSequency[proc_comm.src_proc],
							mappingSequency[proc_comm.dst_proc], proc_comm.BW);
					ProcComm iter = Q.get(0);
					for (Iterator<ProcComm> iterator = Q.iterator(); iterator
							.hasNext();) {
						ProcComm procComm = (ProcComm) iterator.next();
						if ((procComm.adaptivity < iter.adaptivity)
								|| (procComm.adaptivity == iter.adaptivity && procComm.BW > iter.BW))
							break;
					}
					Q.add(proc_comm);
				}
				if (gProcess[new_proc].getFromBandwidthRequirement()[old_proc] > 0) {
					ProcComm proc_comm = new ProcComm();
					proc_comm.src_proc = i;
					proc_comm.dst_proc = cur_stage;
					proc_comm.BW = gProcess[new_proc]
							.getFromBandwidthRequirement()[old_proc];
					proc_comm.adaptivity = calc_adaptivity(
							mappingSequency[proc_comm.src_proc],
							mappingSequency[proc_comm.dst_proc], proc_comm.BW);
					ProcComm iter = Q.get(0);
					for (Iterator<ProcComm> iterator = Q.iterator(); iterator
							.hasNext();) {
						ProcComm procComm = (ProcComm) iterator.next();
						if ((procComm.adaptivity < iter.adaptivity)
								|| (procComm.adaptivity == iter.adaptivity && procComm.BW > iter.BW))
							break;
					}
					Q.add(proc_comm);
				}
			}
		}
		// now route the traffic
		for (int i = 0; i < Q.size(); i++) {
			int src_proc = Q.get(i).src_proc;
			int dst_proc = Q.get(i).dst_proc;
			int src_tile = mappingSequency[src_proc];
			int dst_tile = mappingSequency[dst_proc];
			int BW = Q.get(i).BW;
			if (RoutingEffort.EASY.equals(routingEffort)) {
				if (!route_traffic_easy(src_tile, dst_tile, BW, commit,
						update_routing_table))
					return false;
			} else {
				if (!route_traffic_hard(src_tile, dst_tile, BW, commit,
						update_routing_table))
					return false;
			}
		}
		return true;
	}

	// currently there is only two levels of adaptivity. 0 for no adaptivity, 1
	// for (maybe)
	// some adaptivity
	private final int calc_adaptivity(int src_tile, int dst_tile, int BW) {
		int adaptivity;
		if (gTile[src_tile].getRow() == gTile[dst_tile].getRow()
				|| gTile[src_tile].getColumn() == gTile[dst_tile].getColumn()) {
			adaptivity = 0;
			return adaptivity;
		}

		int[][][] BW_usage = R_syn_link_BW_usage;

		adaptivity = 1;
		int row = gTile[src_tile].getRow();
		int col = gTile[src_tile].getColumn();
		int direction = -2;
		while (row != gTile[dst_tile].getRow()
				|| col != gTile[dst_tile].getColumn()) {
			// For west-first routing
			if (legalTurnSet.WEST_FIRST.equals(legalTurnSet)) {
				if (col > gTile[dst_tile].getColumn()) // step west
					return 0;
				else if (col == gTile[dst_tile].getColumn())
					return 0;
				else if (row == gTile[dst_tile].getRow())
					return 0;
				// Here comes the flexibility. We can choose whether to go
				// vertical or horizontal
				else {
					int direction1 = (row < gTile[dst_tile].getRow()) ? NORTH
							: SOUTH;
					if (BW_usage[row][col][direction1] + BW < linkBandwidth
							&& BW_usage[row][col][EAST] + BW < linkBandwidth)
						return 1;
					direction = (BW_usage[row][col][direction1] < BW_usage[row][col][EAST]) ? direction1
							: EAST;
				}
			}
			// For odd-even routing
			else if (legalTurnSet.ODD_EVEN.equals(legalTurnSet)) {
				int e0 = gTile[dst_tile].getColumn() - col;
				int e1 = gTile[dst_tile].getRow() - row;
				if (e0 == 0) // currently the same column as destination
					direction = (e1 > 0) ? NORTH : SOUTH;
				else {
					if (e0 > 0) { // eastbound messages
						if (e1 == 0)
							direction = EAST;
						else {
							int direction1 = -1, direction2 = -1;
							if (col % 2 == 1
									|| col == gTile[src_tile].getColumn())
								direction1 = (e1 > 0) ? NORTH : SOUTH;
							if (gTile[dst_tile].getColumn() % 2 == 1 || e0 != 1)
								direction2 = EAST;
							if (direction1 == -1)
								direction = direction2;
							else if (direction2 == -1)
								direction = direction1;
							else {// we have two choices
								if (BW_usage[row][col][direction1] + BW < linkBandwidth
										&& BW_usage[row][col][direction2] + BW < linkBandwidth)
									return 1;
								direction = (BW_usage[row][col][direction1] < BW_usage[row][col][direction2]) ? direction1
										: direction2;
							}
						}
					} else { // westbound messages
						if (col % 2 != 0 || e1 == 0)
							direction = WEST;
						else {
							int direction1 = (e1 > 0) ? NORTH : SOUTH;
							if (BW_usage[row][col][direction1] + BW < linkBandwidth
									&& BW_usage[row][col][WEST] + BW < linkBandwidth)
								return 1;
							direction = (BW_usage[row][col][WEST] < BW_usage[row][col][direction1]) ? WEST
									: direction1;
						}
					}
				}
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
				System.err.println("Error");
				;
				break;
			}
		}
		return 0;
	}

	// Route the traffic from src_tile to dst_tile using BW
	// Two routing methods are provided: west-first and odd-even routing
	private boolean route_traffic_easy(int src_tile, int dst_tile, int BW,
			boolean commit, boolean update_routing_table) {
		int row = gTile[src_tile].getRow();
		int col = gTile[src_tile].getColumn();

		int[][][] BW_usage = commit ? R_syn_link_BW_usage
				: R_syn_link_BW_usage_temp;
		int direction = -2;
		while (row != gTile[dst_tile].getRow()
				|| col != gTile[dst_tile].getRow()) {
			// For west-first routing
			if (LegalTurnSet.WEST_FIRST.equals(legalTurnSet)) {
				if (col > gTile[dst_tile].getColumn()) // step west
					direction = WEST;
				else if (col == gTile[dst_tile].getColumn())
					direction = (row < gTile[dst_tile].getRow()) ? NORTH
							: SOUTH;
				else if (row == gTile[dst_tile].getRow())
					direction = EAST;
				// Here comes the flexibility. We can choose whether to go
				// vertical or horizontal

				/*
				 * else { int direction1 = (row<dst.row)?NORTH:SOUTH; if
				 * (BW_usage[row][col][direction1] < BW_usage[row][col][EAST])
				 * direction = direction1; else if
				 * (BW_usage[row][col][direction1] > BW_usage[row][col][EAST])
				 * direction = EAST; else { //In this case, we select the
				 * direction which has the longest //distance to the destination
				 * if ((dst.col-col)*(dst.col-col) <=
				 * (dst.row-row)*(dst.row-row)) direction = direction1; else
				 * //Horizontal move direction = EAST; } }
				 */
				else {
					direction = EAST;
					if (BW_usage[row][col][direction] + BW > linkBandwidth)
						direction = (row < gTile[dst_tile].getRow()) ? NORTH
								: SOUTH;
				}
			}
			// For odd-even routing
			else if (LegalTurnSet.ODD_EVEN.equals(legalTurnSet)) {
				int e0 = gTile[dst_tile].getColumn() - col;
				int e1 = gTile[dst_tile].getRow() - row;
				if (e0 == 0) // currently the same column as destination
					direction = (e1 > 0) ? NORTH : SOUTH;
				else {
					if (e0 > 0) { // eastbound messages
						if (e1 == 0)
							direction = EAST;
						else {
							int direction1 = -1, direction2 = -1;
							if (col % 2 == 1
									|| col == gTile[src_tile].getColumn())
								direction1 = (e1 > 0) ? NORTH : SOUTH;
							if (gTile[dst_tile].getColumn() % 2 == 1 || e0 != 1)
								direction2 = EAST;
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
								direction = (BW_usage[row][col][direction1] < BW_usage[row][col][direction2]) ? direction1
										: direction2;
						}
					} else { // westbound messages
						if (col % 2 != 0 || e1 == 0)
							direction = WEST;
						else {
							int direction1 = (e1 > 0) ? NORTH : SOUTH;
							direction = (BW_usage[row][col][WEST] < BW_usage[row][col][direction1]) ? WEST
									: direction1;
						}
					}
				}
			}

			BW_usage[row][col][direction] += BW;
			if (BW_usage[row][col][direction] > linkBandwidth
					&& (!update_routing_table))
				return false;
			if (update_routing_table)
				routing_table[row][col][src_tile][dst_tile] = direction;

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
	private boolean route_traffic_hard(int src_tile, int dst_tile, int BW,
			boolean commit, boolean update_routing_table) {
		int row = gTile[src_tile].getRow();
		int col = gTile[src_tile].getColumn();

		int[][][] BW_usage = commit ? R_syn_link_BW_usage
				: R_syn_link_BW_usage_temp;

		// We can arrive at any destination with 2*gEdgeSize hops
		if (routing_bit_array == null) {
			routing_bit_array = new int[2 * gEdgeSize];
		}
		if (best_routing_bit_array == null) {
			best_routing_bit_array = new int[2 * gEdgeSize];
		}

		// In the following, we find the routing path which has the minimal
		// maximal
		// link BW usage and store that routing path to best_routing_bit_array
		int min_path_BW = Integer.MAX_VALUE;
		int x_hop = gTile[src_tile].getColumn() - gTile[dst_tile].getColumn();
		x_hop = (x_hop >= 0) ? x_hop : (0 - x_hop);
		int y_hop = gTile[src_tile].getRow() - gTile[dst_tile].getRow();
		y_hop = (y_hop >= 0) ? y_hop : (0 - y_hop);

		init_routing_path_generator(x_hop, y_hop);

		while (next_routing_path(x_hop, y_hop)) { // For each path
			int usage = path_BW_usage(row, col, gTile[dst_tile].getRow(),
					gTile[dst_tile].getColumn(), BW_usage, BW);
			if (usage < min_path_BW) {
				min_path_BW = usage;
				best_routing_bit_array = Arrays.copyOf(routing_bit_array, x_hop
						+ y_hop);
			}
		}

		if (min_path_BW == Integer.MAX_VALUE)
			return false;

		int direction = -2;

		int hop_id = 0;
		while (row != gTile[dst_tile].getRow()
				|| col != gTile[dst_tile].getColumn()) {

			if (best_routing_bit_array[hop_id++] != 0)
				direction = (row < gTile[dst_tile].getRow()) ? NORTH : SOUTH;
			else
				direction = (col < gTile[dst_tile].getColumn()) ? EAST : WEST;

			BW_usage[row][col][direction] += BW;

			if ((BW_usage[row][col][direction] > linkBandwidth)
					&& (!update_routing_table))
				return false;
			if (update_routing_table)
				routing_table[row][col][src_tile][dst_tile] = direction;

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
				System.err.println("Error");
				break;
			}
		}
		return true;
	}

	/**********************************************************************
	 * This function needs to fulfill two tasks. First, check to see * whether
	 * it's a valid path according to the selected routing alg. * Second, it
	 * checks to see if any BW requirement violates. If either * of the two
	 * conditions is not met, return INT_MAX. *
	 **********************************************************************/
	private int path_BW_usage(int srcRow, int srcColumn, int dstRow,
			int dstColumn, int[][][] BW_usage, int BW) {
		int row = srcRow;
		int col = srcColumn;

		int max_BW = 0;
		int hop_id = 0;

		while (row != dstRow || col != dstColumn) {
			int direction = -2;
			// For west-first routing
			if (LegalTurnSet.WEST_FIRST.equals(legalTurnSet)) {
				if (col > dstColumn) { // step west
					direction = WEST;
					if (routing_bit_array[hop_id] != 0)
						return Integer.MAX_VALUE;
				} else if (col == dstColumn) {
					direction = (row < dstRow) ? NORTH : SOUTH;
					if (routing_bit_array[hop_id] == 0)
						return Integer.MAX_VALUE;
				} else if (row == dstRow) {
					direction = EAST;
					if (routing_bit_array[hop_id] != 0)
						return Integer.MAX_VALUE;
				}
				// Here comes the flexibility. We can choose whether to go
				// vertical or horizontal
				else {
					int direction1 = (row < dstRow) ? NORTH : SOUTH;
					int direction2 = EAST;
					direction = (routing_bit_array[hop_id] != 0) ? direction1
							: direction2;
				}
			}
			// For odd-even routing
			else if (LegalTurnSet.ODD_EVEN.equals(legalTurnSet)) {
				int e0 = dstColumn - col;
				int e1 = dstRow - row;
				if (e0 == 0) { // currently the same column as destination
					direction = (e1 > 0) ? NORTH : SOUTH;
					if (routing_bit_array[hop_id] == 0)
						return Integer.MAX_VALUE;
				} else {
					if (e0 > 0) { // eastbound messages
						if (e1 == 0) {
							direction = EAST;
							if (routing_bit_array[hop_id] != 0)
								return Integer.MAX_VALUE;
						} else {
							int direction1 = -1, direction2 = -1;
							if (col % 2 == 1 || col == srcColumn)
								direction1 = (e1 > 0) ? NORTH : SOUTH;
							if (dstColumn % 2 == 1 || e0 != 1)
								direction2 = EAST;
							assert (!(direction1 == -1 && direction2 == -1));
							if (direction1 == -1) {
								direction = direction2;
								if (routing_bit_array[hop_id] != 0)
									return Integer.MAX_VALUE;
							} else if (direction2 == -1) {
								direction = direction1;
								if (routing_bit_array[hop_id] == 0)
									return Integer.MAX_VALUE;
							} else
								// we have two choices
								direction = (routing_bit_array[hop_id] != 0) ? direction1
										: direction2;
						}
					} else { // westbound messages
						if (col % 2 != 0 || e1 == 0) {
							direction = WEST;
							if (routing_bit_array[hop_id] != 0)
								return Integer.MAX_VALUE;
						} else {
							int direction1 = (e1 > 0) ? NORTH : SOUTH;
							int direction2 = WEST;
							direction = (routing_bit_array[hop_id] != 0) ? direction1
									: direction2;
						}
					}
				}
			}

			if (BW_usage[row][col][direction] > max_BW)
				max_BW = BW_usage[row][col][direction];

			if (BW_usage[row][col][direction] + BW > linkBandwidth)
				return Integer.MAX_VALUE;

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
				System.err.println("Error");
				break;
			}
			hop_id++;

		}
		return 1;
	}

	private boolean init_routing_path_generator(int x_hop, int y_hop) {
		first_routing_path = true;
		MAX_ROUTING_INT = 0;
		for (int index = 0; index < y_hop; index++)
			MAX_ROUTING_INT = (MAX_ROUTING_INT << 1) + 1;
		MAX_ROUTING_INT = MAX_ROUTING_INT << x_hop;
		return true;
	}

	private boolean next_routing_path(int x_hop, int y_hop) {
		if (first_routing_path) {
			first_routing_path = false;
			int index = 0;
			routing_int = 0;
			for (index = 0; index < y_hop; index++) {
				routing_bit_array[index] = 1;
				routing_int = (routing_int << 1) + 1;
			}
			for (int x_index = 0; x_index < x_hop; x_index++)
				routing_bit_array[index + x_index] = 0;
			return true;
		}

		/**********************************************************************
		 * find the next routing path based on the current routing_bit_array *
		 * the next one is the one which is the minimal array which is larger *
		 * than the current routing_bit_array but with the same number of 1s *
		 **********************************************************************/
		while (routing_int <= MAX_ROUTING_INT) {
			if (routing_int % 2 == 0) // For an even number
				routing_int += 2;
			else
				routing_int++;
			if (one_bits(routing_int, y_hop))
				break;
		}
		if (routing_int <= MAX_ROUTING_INT)
			return true;
		else
			return false;
	}

	// Returns true if the binary representation of r contains y_hop number of
	// 1s. It also assigns the bit form to routing_bit_array
	private boolean one_bits(int r, int onebits) {
		routing_bit_array = new int[2 * gEdgeSize];
		Arrays.fill(routing_bit_array, 0);
		int index = 0;
		int cur_one_bits = 0;
		while (r != 0) {
			routing_bit_array[index] = r & 1;
			if (routing_bit_array[index] != 0)
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

	boolean program_routers() {
		generate_routing_table();
		// clean all the old routing table
		for (int tile_id = 0; tile_id < gTileNum; tile_id++) {
			for (int src_tile = 0; src_tile < gTileNum; src_tile++) {
				for (int dst_tile = 0; dst_tile < gTileNum; dst_tile++) {
					if (tile_id == dst_tile)
						gTile[tile_id].setRoutingEntry(src_tile, dst_tile, -1);
					else
						gTile[tile_id].setRoutingEntry(src_tile, dst_tile, -2);
				}
			}
		}

		for (int row = 0; row < gEdgeSize; row++) {
			for (int col = 0; col < gEdgeSize; col++) {
				int tile_id = row * gEdgeSize + col;
				for (int src_tile = 0; src_tile < gTileNum; src_tile++) {
					for (int dst_tile = 0; dst_tile < gTileNum; dst_tile++) {
						int link_id = locateLink(row, col,
								routing_table[row][col][src_tile][dst_tile]);
						if (link_id != -1)
							gTile[tile_id].setRoutingEntry(src_tile, dst_tile,
									link_id);
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
		int link_id;
		for (link_id = 0; link_id < gLinkNum; link_id++) {
			if (gTile[gLink[link_id].getFromTileId()].getRow() == origRow
					&& gTile[gLink[link_id].getFromTileId()].getColumn() == origColumn
					&& gTile[gLink[link_id].getToTileId()].getRow() == row
					&& gTile[gLink[link_id].getToTileId()].getColumn() == column)
				break;
		}
		if (link_id == gLinkNum) {
			System.err.println("Error in locating link");
			System.exit(-1);
		}
		return link_id;
	}

	private void generate_routing_table() {
		// reset all the BW_usage
		for (int i = 0; i < gEdgeSize; i++)
			for (int j = 0; j < gEdgeSize; j++)
				for (int k = 0; k < 4; k++)
					R_syn_link_BW_usage[i][j][k] = 0;

		routing_table = new int[gEdgeSize][gEdgeSize][gTileNum][gTileNum];
		for (int i = 0; i < gEdgeSize; i++) {
			for (int j = 0; j < gEdgeSize; j++) {
				for (int k = 0; k < gTileNum; k++) {
					for (int m = 0; m < gTileNum; m++)
						routing_table[i][j][k][m] = -2;
				}
			}
		}

		// if it's a real child mappingnode.
		if (stage == gProcNum)
			route_traffics(0, gProcNum - 1, true, true);
		// if it's the node which generate min upperBound
		else {
			for (int i = 0; i < stage; i++)
				route_traffics(i, i, true, true);
			route_traffics(stage, gProcNum - 1, true, true);
		}
	}

}
