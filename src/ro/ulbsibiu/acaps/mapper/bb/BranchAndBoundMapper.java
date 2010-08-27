package ro.ulbsibiu.acaps.mapper.bb;

import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.sa.Link;
import ro.ulbsibiu.acaps.mapper.sa.Process;
import ro.ulbsibiu.acaps.mapper.sa.Tile;

public class BranchAndBoundMapper implements Mapper {

	private static final int DUMMY_VOL = Integer.MAX_VALUE / 100;

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
	 * whether or not to build routing table too. When the SA algorithm builds
	 * the routing table, the mapping process takes more time but, this should
	 * yield better performance
	 */
	private boolean buildRoutingTable;

	/** the size of the Priority Queue */
	private int pqSize;

	private int minHitThreshold;

	private int[][] procMatrix = null;

	private float[][] archMatrix = null;

	// proc_map_array[i] represents the actual process that the i-th mapped
	// process
	// corresponding to
	private int[] proc_map_array = null;

	static final double MAX_VALUE = Integer.MAX_VALUE - 100;

	/**
	 * each newly mapped communication transaction should be less than this
	 * value. Useful for non-regular region mapping
	 */
	static final double MAX_PER_TRAN_COST = MAX_VALUE;

	public enum RoutingEffort {
		EASY, HARD
	}

	public BranchAndBoundMapper() {

	}

	// Initialization function for Branch-and-bound mapping
	private void BBMInit() {
		System.out.println("Initialize for branch-and-bound");
		proc_map_array = new int[gProcNum];
		BBMSortProcesses();
		BBMBuildProcessMatrix();
		BBMBuildArchitectureMatrix();

		// if (exist_non_regular_regions()) {
		// // let's calculate the maximum ebit of sending a bit
		// // from one tile to its neighboring tile
		// double max_e = -1;
		// for (int i = 0; i < gLinkNum; i++) {
		// if (gLink[i].getCost() > max_e)
		// max_e = gLink[i].getCost();
		// }
		// double eb = max_e;
		// max_e = -1;
		// for (int i = 0; i < gTileNum; i++) {
		// if (gTile[i].getCost() > max_e)
		// max_e = gTile[i].getCost();
		// }
		// eb += max_e * 2;
		// MAX_PER_TRAN_COST = eb * DUMMY_VOL * 1.3; // let's put some overhead
		// }

	}

	void BBMBuildProcessMatrix() {
		procMatrix = new int[gProcNum][gProcNum];
		for (int i = 0; i < gProcNum; i++) {
			int row = gProcess[i].getRank();
			for (int j = 0; j < gProcNum; j++) {
				int col = gProcess[j].getRank();
				procMatrix[row][col] = gProcess[i].getFromCommunication()[j]
						+ gProcess[i].getToCommunication()[j];
			}
		}
		// Sanity checking
		for (int i = 0; i < gProcNum; i++) {
			for (int j = 0; j < gProcNum; j++) {
				if (procMatrix[i][j] < 0) {
					System.err.println("Error for < 0");
					System.exit(1);
				}
				if (procMatrix[i][j] != procMatrix[j][i]) {
					System.err
							.println("Error. The process matrix is not symetric.");
					System.exit(1);
				}
			}
		}
	}

	void BBMBuildArchitectureMatrix() {
		archMatrix = new float[gTileNum][gTileNum];
		for (int src = 0; src < gTileNum; src++) {
			for (int dst = 0; dst < gTileNum; dst++) {
				float energy = 0;
				Tile currentTile = gTile[src];
				energy += currentTile.getCost();
				while (currentTile.getTileId() != dst) {
					int linkId = currentTile.routeToLink(src, dst);
					Link pL = gLink[linkId];
					energy += pL.getCost();
					currentTile = gTile[pL.getToTileId()];
					energy += currentTile.getCost();
				}
				archMatrix[src][dst] = energy;
			}
		}
		// Sanity checking
		for (int i = 0; i < gTileNum; i++) {
			for (int j = 0; j < gTileNum; j++) {
				if (archMatrix[i][j] != archMatrix[j][i]) {
					System.err
							.println("Error. The architecture matrix is not symetric.");
					System.exit(1);
				}
			}
		}
	}

	// sort the processes so that the branch-and-bound mapping can be
	// accelerated
	void BBMSortProcesses() {
		// sort them according to the sum of each process's ingress and egress
		// communication volume
		for (int i = 0; i < gProcess.length; i++) {
			gProcess[i].setTotalCommVol(0);
			for (int k = 0; k < gProcess.length; k++) {
				gProcess[i].setTotalCommVol(gProcess[i].getTotalCommVol()
						+ gProcess[i].getToCommunication()[k]
						+ gProcess[i].getFromCommunication()[k]);
			}
		}
		// Now rank them
		int cur_rank = 0;
		// locked PEs have the highest priority
		// for (int i=0; i<gProcess.length; i++) {
		// if (gProcess[i].isLocked()) {
		// proc_map_array[cur_rank] = i;
		// gProcess[i].setRank(cur_rank ++);
		// }
		// else {
		// gProcess[i].setRank(-1);
		// }
		// }
		// the remaining PEs are sorted based on their comm volume
		for (int i = cur_rank; i < gProcess.length; i++) {
			int max = -1;
			int maxid = -1;
			for (int k = 0; k < gProcNum; k++) {
				if (gProcess[k].getRank() != -1) {
					continue;
				}
				if (gProcess[k].getTotalCommVol() > max) {
					max = gProcess[k].getTotalCommVol();
					maxid = k;
				}
			}
			gProcess[maxid].setRank(i);
			proc_map_array[i] = maxid;
		}
	}

	private void branchAndBoundMapping() {
		BBMInit();
		double minCost = MAX_VALUE;
		double minUpperBound = MAX_VALUE;
		PQueue Q = new PQueue();

		// if (exist_locked_pe()) {
		// // this ruins the symmetric structure of the system completely.
		// // although for some corner cases, symmetry still exists, we don't
		// // consider it here.
		// for (int i = 0; i < gEdgeSize; i++) {
		// for (int j = 0; j < gEdgeSize; j++) {
		// MappingNode pNode = new MappingNode(i * gEdgeSize + j);
		// if (!pNode.isIllegal()) {
		// Q.insert(pNode);
		// }
		// }
		// }
		// } else {
		// To exploit the symmetric structure of the system, we only need
		// to map the first processes to one corner of the chip, as shown
		// in the following code.
		/*****************************************************************
		 * And if we need to synthesize the routing table, then there is not
		 * much symmetry property to be exploited
		 *****************************************************************/
		if (!buildRoutingTable) {
			int size = (gEdgeSize + 1) / 2;
			for (int i = 0; i < size; i++) {
				for (int j = 0; j <= i; j++) {
					MappingNode pNode = new MappingNode(i * gEdgeSize + j);
					// if (!pNode.isIllegal()) {
					Q.insert(pNode);
					// }
				}
			}
		} else {
			// for west-first or odd-even, we only need to consider the
			// bottom half
			int size = (gEdgeSize + 1) / 2;
			for (int i = 0; i < size; i++) {
				for (int j = 0; j < gEdgeSize; j++) {
					MappingNode pNode = new MappingNode(i * gEdgeSize + j);
					// if (!pNode.isIllegal()) {
					Q.insert(pNode);
					// }
				}
			}
		}
		// }

		MappingNode bestMapping = null;
		int min_upperbound_hit_cnt = 0;

		while (!Q.empty()) {
			MappingNode pNode = Q.next();
			if (pNode.cost > minCost || pNode.lowerBound > minUpperBound) {
				continue;
			}

			boolean insertAllFlag = false;
			int prev_insert = 0;
			/**********************************************************************
			 * Change this to adjust the tradeoff between the solution quality *
			 * and the run time *
			 **********************************************************************/
			if (Q.length() < pqSize) {
				insertAll(pNode, minUpperBound, min_upperbound_hit_cnt,
						insertAllFlag, prev_insert, minCost, bestMapping, Q);
				continue;
			} else {
				selectiveInsert(pNode, minUpperBound, min_upperbound_hit_cnt,
						minCost, bestMapping, insertAllFlag, Q, prev_insert);
			}
		}
		System.out.println("Totally " + MappingNode.cnt
				+ " have been generated");
		if (bestMapping != null) {
			BBMMap(bestMapping);
		} else {
			System.out.println("Can not find a suitable solution.");
		}
	}

	private void insertAll(MappingNode pNode, double minUpperBound,
			int min_upperbound_hit_cnt, boolean insertAllFlag, int prev_insert,
			double minCost, MappingNode bestMapping, PQueue Q) {
		if (pNode.upperBound == minUpperBound && minUpperBound < MAX_VALUE
				&& min_upperbound_hit_cnt <= minHitThreshold)
			insertAllFlag = true;
		for (int i = prev_insert; i < gTileNum; i++) {
			if (pNode.Expandable(i)) {
				MappingNode child = new MappingNode(pNode, i, false); // FIXME
																		// 3rd
																		// param
																		// is
																		// false?
				if (child.lowerBound > minUpperBound || child.cost > minCost
						|| (child.cost == minCost && bestMapping != null)
				/* || child.isIllegal() */)
					continue;
				else {
					if (child.upperBound < minUpperBound) {
						minUpperBound = child.upperBound;
						System.out
								.println("Current minimum cost upper bound is "
										+ minUpperBound);
						min_upperbound_hit_cnt = 0;

						// some new stuff here: we keep the mapping with
						// min upperBound
						if (buildRoutingTable) {
							if (child.upperBound < minCost) {
								bestMapping = new MappingNode(child);
								minCost = child.upperBound;
							} else if (child.upperBound < minUpperBound
									&& bestMapping != null)
								bestMapping = new MappingNode(child);
						}
					}
					if (child.stage == gProcNum) {
						minCost = child.cost;
						if (child.stage < gProcNum)
							minCost = child.upperBound;
						if (minCost < minUpperBound)
							minUpperBound = minCost;
						System.out
								.println("Current minimum cost is " + minCost);
						bestMapping = child;
					} else {
						Q.insert(child);
						if (Q.length() >= pqSize && !insertAllFlag) {
							prev_insert = i;
							selectiveInsert(pNode, minUpperBound,
									min_upperbound_hit_cnt, minCost,
									bestMapping, insertAllFlag, Q, prev_insert);
						}
					}
				}
			}
		}
	}

	private void selectiveInsert(MappingNode pNode, double minUpperBound,
			int min_upperbound_hit_cnt, double minCost,
			MappingNode bestMapping, boolean insertAllFlag, PQueue Q,
			int prev_insert) {
		if ((Math.abs(pNode.upperBound - minUpperBound) == 0.01)
				&& minUpperBound < MAX_VALUE
				&& min_upperbound_hit_cnt <= minHitThreshold) {
			min_upperbound_hit_cnt++;
			insertAll(pNode, minUpperBound, min_upperbound_hit_cnt,
					insertAllFlag, prev_insert, minCost, bestMapping, Q);
		}
		// In this case, we only select one child which has the
		// smallest partial cost. However, if the node is currently
		// the one with the minUpperBound, then its child which
		// is generated by the corresponding minUpperBound is
		// also generated
		int index = pNode.bestCostCandidate();
		MappingNode child = new MappingNode(pNode, index, false); // FIXME
																	// is
																	// 3rd
																	// param
																	// false?
		if (child.lowerBound > minUpperBound || child.cost > minCost
				|| (child.cost == minCost && bestMapping != null))
			return;
		else {
			if (child.upperBound < minUpperBound - 0.01) {
				// In this case, we should also insert other children
				insertAllFlag = true;
				insertAll(pNode, minUpperBound, min_upperbound_hit_cnt,
						insertAllFlag, prev_insert, minCost, bestMapping, Q);
			}
			if (child.stage == gProcNum || child.lowerBound == child.upperBound) {
				minCost = child.cost;
				if (child.stage < gProcNum)
					minCost = child.upperBound;
				if (minCost < minUpperBound)
					minUpperBound = minCost;
				System.out.println("Current minimum cost is " + minCost);
				bestMapping = child;
			} else
				Q.insert(child);
		}

		if (pNode.upperBound > minUpperBound || pNode.upperBound == MAX_VALUE) {
			return;
		}

		if (index == pNode.bestUpperBoundCandidate()) {
			return;
		}

		index = pNode.bestUpperBoundCandidate();
		if (!pNode.Expandable(index)) {
			System.err.println("Error in expanding at stage " + pNode.Stage());
			System.err.println("index = " + index);
			System.exit(-1);
		}
		child = new MappingNode(pNode, index, false); // FIXME is the
														// 3rd param
														// false?
		if (child.lowerBound > minUpperBound || child.cost > minCost)
			return;
		else {
			if (child.upperBound < minUpperBound) {
				minUpperBound = child.upperBound;
				System.out.println("Current minimum cost upper bound is "
						+ minUpperBound);
				min_upperbound_hit_cnt = 0;
			}
			if (child.stage == gProcNum || child.lowerBound == child.upperBound) {
				if (minCost == child.cost && bestMapping != null)
					return;
				else {
					minCost = child.cost;
					if (child.stage < gProcNum)
						minCost = child.upperBound;
					if (minCost < minUpperBound)
						minUpperBound = minCost;
					System.out.println("Current minimum cost is " + minCost);
					bestMapping = child;
				}
			} else
				Q.insert(child);
		}
	}

	private void BBMMap(MappingNode bestMapping) {
		for (int i = 0; i < gProcNum; i++)
			gProcess[i].setTileId(-1);
		for (int i = 0; i < gTileNum; i++)
			gTile[i].setProcId(-1);
		for (int i = 0; i < gProcNum; i++) {
			int procId = proc_map_array[i];
			gProcess[procId].setTileId(bestMapping.mapToTile(i));
			gTile[bestMapping.mapToTile(i)].setProcId(procId);
		}
		if (buildRoutingTable)
			bestMapping.program_routers();
	}

	@Override
	public String map() throws TooFewNocNodesException {
		// TODO Auto-generated method stub

		long start = System.currentTimeMillis();
		System.out.println("Start mapping...");

		assert (gProcNum == ((int) gProcess.length));

		branchAndBoundMapping();
		long end = System.currentTimeMillis();
		System.out.println("Mapping process finished successfully.");
		System.out.println("Time: " + (end - start) / 1000 + " seconds");

		return null;
	}

}
