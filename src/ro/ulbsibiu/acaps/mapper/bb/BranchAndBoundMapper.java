package ro.ulbsibiu.acaps.mapper.bb;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.sa.Link;
import ro.ulbsibiu.acaps.mapper.sa.Process;
import ro.ulbsibiu.acaps.mapper.sa.Tile;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;

/**
 * Branch-and-Bound algorithm for Network-on-Chip (NoC) application mapping. The
 * implementation is based on the one from <a
 * href="http://www.ece.cmu.edu/~sld/wiki/doku.php?id=shared:nocmap">NoCMap</a>
 * 
 * <p>
 * Note that currently, this algorithm works only with N x N 2D mesh NoCs
 * </p>
 * 
 * @author cipi
 * 
 */
public class BranchAndBoundMapper implements Mapper {

	// private static final int DUMMY_VOL = Integer.MAX_VALUE / 100;

	static final int NORTH = 0;

	static final int SOUTH = 1;

	static final int EAST = 2;

	static final int WEST = 3;

	/** the number of tiles (nodes) from the NoC */
	int gTileNum;

	/**
	 * the size of the 2D mesh, sqrt(gTileNum) (sqrt(gTileNum) * sqrt(gTileNum)
	 * = gTileNum)
	 */
	int gEdgeSize;

	/**
	 * the number of processes (tasks). Note that each core has only one task
	 * associated to it.
	 */
	int gProcNum;

	/**
	 * the number of links from the NoC
	 */
	int gLinkNum;

	/** the tiles from the Network-on-Chip (NoC) */
	Tile[] gTile;

	/** the processes (tasks, cores) */
	Process[] gProcess;

	/** the communication channels from the NoC */
	Link[] gLink;

	/**
	 * what links are used by tiles to communicate (each source - destination
	 * tile pair has a list of link IDs). The matrix must have size
	 * <tt>gTileNum x gTileNum</tt>. <b>This must be <tt>null</tt> when
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

	RoutingEffort routingEffort;

	int linkBandwidth;
	
	private float bufReadEBit;
	
	private float bufWriteEBit;
	
	/** the size of the Priority Queue */
	private int priorityQueueSize;

	private int minHitThreshold;

	int[][] procMatrix = null;

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

	/**
	 * @author cipi
	 * 
	 */
	public enum RoutingEffort {
		EASY, HARD
	}

	private float minUpperBound;

	private int minUpperBoundHitCount;

	private boolean insertAllFlag;

	private int previousInsert;

	private float minCost;

	private MappingNode bestMapping;

	/**
	 * Constructor
	 * 
	 * @param gTileNum
	 *            the size of the 2D mesh (gTileNum * gTileNum)
	 * @param gProcNum
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
	public BranchAndBoundMapper(int gTileNum, int gProcNum, int linkBandwidth,
			int priorityQueueSize, float bufReadEBit, float bufWriteEBit) {
		this(gTileNum, gProcNum, linkBandwidth, priorityQueueSize, false,
				LegalTurnSet.WEST_FIRST, bufReadEBit, bufWriteEBit);
	}

	/**
	 * Constructor
	 * 
	 * @param gTileNum
	 *            the size of the 2D mesh (gTileNum * gTileNum)
	 * @param gProcNum
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
	public BranchAndBoundMapper(int gTileNum, int gProcNum, int linkBandwidth,
			int priorityQueueSize, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit) {
		this.gTileNum = gTileNum;
		this.gEdgeSize = (int) Math.sqrt(gTileNum);
		this.gProcNum = gProcNum;
		this.linkBandwidth = linkBandwidth;
		this.priorityQueueSize = priorityQueueSize;
		// we have 2gEdgeSize(gEdgeSize - 1) bidirectional links =>
		// 4gEdgeSize(gEdgeSize - 1) unidirectional links
		this.gLinkNum = 2 * (gEdgeSize - 1) * gEdgeSize * 2;
		this.buildRoutingTable = buildRoutingTable;
		this.legalTurnSet = legalTurnSet;
		this.bufReadEBit = bufReadEBit;
		this.bufWriteEBit = bufWriteEBit;

		gTile = new Tile[gTileNum];

		gProcess = new Process[gProcNum];

		gLink = new Link[gLinkNum];
	}

	public void initializeCores() {
		for (int i = 0; i < gProcess.length; i++) {
			gProcess[i] = new Process(i, -1);
			gProcess[i].setFromCommunication(new int[gTileNum]);
			gProcess[i].setToCommunication(new int[gTileNum]);
			gProcess[i].setFromBandwidthRequirement(new int[gTileNum]);
			gProcess[i].setToBandwidthRequirement(new int[gTileNum]);
		}
	}

	public void initializeNocTopology(float switchEBit, float linkEBit) {
		// initialize nodes
		for (int i = 0; i < gTile.length; i++) {
			gTile[i] = new Tile(i, -1, i / gEdgeSize, i % gEdgeSize, switchEBit);
		}
		// initialize links
		for (int i = 0; i < gLink.length; i++) {
			// There are totally 2*(gEdgeSize-1)*gEdgeSize*2 links. The first
			// half links are horizontal
			// the second half links are vertical links.
			int fromTileRow;
			int fromTileColumn;
			int toTileRow;
			int toTileColumn;
			if (i < 2 * (gEdgeSize - 1) * gEdgeSize) {
				fromTileRow = i / (2 * (gEdgeSize - 1));
				toTileRow = i / (2 * (gEdgeSize - 1));
				int localId = i % (2 * (gEdgeSize - 1));
				if (localId < (gEdgeSize - 1)) {
					// from west to east
					fromTileColumn = localId;
					toTileColumn = localId + 1;
				} else {
					// from east to west
					localId = localId - (gEdgeSize - 1);
					fromTileColumn = localId + 1;
					toTileColumn = localId;
				}
			} else {
				int localId = i - 2 * (gEdgeSize - 1) * gEdgeSize;
				fromTileColumn = localId / (2 * (gEdgeSize - 1));
				toTileColumn = localId / (2 * (gEdgeSize - 1));
				localId = localId % (2 * (gEdgeSize - 1));
				if (localId < (gEdgeSize - 1)) {
					// from south to north
					fromTileRow = localId;
					toTileRow = localId + 1;
				} else {
					// from north to south
					localId = localId - (gEdgeSize - 1);
					fromTileRow = localId + 1;
					toTileRow = localId;
				}
			}

			int fromTileId = fromTileRow * gEdgeSize + fromTileColumn;
			int toTileId = toTileRow * gEdgeSize + toTileColumn;

			gLink[i] = new Link(i, linkBandwidth, fromTileId, toTileId,
					linkEBit);
			gLink[i].setFromTileRow(fromTileRow);
			gLink[i].setFromTileColumn(fromTileColumn);
			gLink[i].setToTileRow(toTileRow);
			gLink[i].setToTileColumn(toTileColumn);
		}
		// attach the links to the NoC nodes
		for (int i = 0; i < gTileNum; i++) {
			for (int j = 0; j < gLink.length; j++) {
				if (gLink[j].getFromTileRow() == gTile[i].getRow()
						&& gLink[j].getFromTileColumn() == gTile[i].getColumn()) {
					gTile[i].addOutLink(gLink[j].getLinkId());
				}
				if (gLink[j].getToTileRow() == gTile[i].getRow()
						&& gLink[j].getToTileColumn() == gTile[i].getColumn()) {
					gTile[i].addInLink(gLink[j].getLinkId());
				}
			}
			assert gTile[i].getInLinkList().size() > 0;
			assert gTile[i].getOutLinkList().size() > 0;
		}
		// for each router generate a routing table provided by the XY routing
		// protocol
		for (int i = 0; i < gTileNum; i++) {
			gTile[i].generateXYRoutingTable(gTileNum, gEdgeSize, gLink);
		}

		generateLinkUsageList();
	}

	private void generateLinkUsageList() {
		if (this.buildRoutingTable == true) {
			linkUsageList = null;
		} else {
			// Allocate the space for the link usage table
			int[][][] linkUsageMatrix = new int[gTileNum][gTileNum][gLinkNum];

			// Setting up the link usage matrix
			for (int srcId = 0; srcId < gTileNum; srcId++) {
				for (int dstId = 0; dstId < gTileNum; dstId++) {
					if (srcId == dstId) {
						continue;
					}
					Tile currentTile = gTile[srcId];
					while (currentTile.getTileId() != dstId) {
						int linkId = currentTile.routeToLink(srcId, dstId);
						Link link = gLink[linkId];
						linkUsageMatrix[srcId][dstId][linkId] = 1;
						currentTile = gTile[link.getToTileId()];
					}
				}
			}

			// Now build the g_link_usage_list
			linkUsageList = new ArrayList[gTileNum][gTileNum];
			for (int src = 0; src < gTileNum; src++) {
				for (int dst = 0; dst < gTileNum; dst++) {
					linkUsageList[src][dst] = new ArrayList<Integer>();
					if (src == dst) {
						continue;
					}
					for (int linkId = 0; linkId < gLinkNum; linkId++) {
						if (linkUsageMatrix[src][dst][linkId] == 1) {
							linkUsageList[src][dst].add(linkId);
						}
					}
				}
			}

			assert this.linkUsageList != null;
			assert linkUsageList.length == gTileNum;
			for (int i = 0; i < linkUsageList.length; i++) {
				assert linkUsageList[i].length == gTileNum;
			}
		}
	}

	private void mapCoresToNocNodesRandomly() {
		// Random rand = new Random();
		// for (int i = 0; i < gTileNum; i++) {
		// int k = Math.abs(rand.nextInt()) % gTileNum;
		// while (gTile[k].getProcId() != -1) {
		// k = Math.abs(rand.nextInt()) % gTileNum;
		// }
		// gProcess[i].setTileId(k);
		// gTile[k].setProcId(i);
		// }

		// this maps the cores like NoCMap does
		int[] coreMap = new int[] { 11, 13, 10, 8, 12, 0, 9, 1, 2, 4, 14, 15,
				5, 3, 7, 6 };
		for (int i = 0; i < gProcNum; i++) {
			gProcess[i].setTileId(coreMap[i]);
			gTile[coreMap[i]].setProcId(i);
		}
	}

	private void printCurrentMapping() {
		for (int i = 0; i < gProcNum; i++) {
			System.out.println("Core " + gProcess[i].getProcId()
					+ " is mapped to NoC node " + gProcess[i].getTileId());
		}
	}

	/**
	 * Initialization function for Branch-and-Bound mapping
	 */
	private void init() {
		System.out.println("Initialize for branch-and-bound");
		procMapArray = new int[gProcNum];
		sortProcesses();
		buildProcessMatrix();
		buildArchitectureMatrix();

		// if (exist_non_regular_regions()) {
		// // let's calculate the maximum ebit of sending a bit
		// // from one tile to its neighboring tile
		// float max_e = -1;
		// for (int i = 0; i < gLinkNum; i++) {
		// if (gLink[i].getCost() > max_e)
		// max_e = gLink[i].getCost();
		// }
		// float eb = max_e;
		// max_e = -1;
		// for (int i = 0; i < gTileNum; i++) {
		// if (gTile[i].getCost() > max_e)
		// max_e = gTile[i].getCost();
		// }
		// eb += max_e * 2;
		// MAX_PER_TRAN_COST = eb * DUMMY_VOL * 1.3; // let's put some overhead
		// }

	}

	private void buildProcessMatrix() {
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

	private void buildArchitectureMatrix() {
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

	/**
	 * sort the processes so that the branch-and-bound mapping can be
	 * accelerated
	 */
	private void sortProcesses() {
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
					MappingNode pNode = new MappingNode(this, i * gEdgeSize + j);
					if (!pNode.isIllegal()) {
						Q.insert(pNode);
					}
				}
			}
		} else {
			// for west-first or odd-even, we only need to consider the
			// bottom half
			int size = (gEdgeSize + 1) / 2;
			for (int i = 0; i < size; i++) {
				for (int j = 0; j < gEdgeSize; j++) {
					MappingNode pNode = new MappingNode(this, i * gEdgeSize + j);
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
			if (pNode.cost > minCost || pNode.lowerBound > minUpperBound) {
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
//		System.out.println("insertAll cnt " + MappingNode.cnt
//				+ " queue length " + Q.length() + " previous insert "
//				+ previousInsert + " minUpperBound " + minUpperBound);
		if (MathUtils.approximatelyEqual(pNode.upperBound, minUpperBound)
				&& MathUtils.definitelyLessThan(minUpperBound, MAX_VALUE)
				&& minUpperBoundHitCount <= minHitThreshold) {
			insertAllFlag = true;
		}
		for (int i = previousInsert; i < gTileNum; i++) {
//			System.out.println("Node expandable at " + i + " " + pNode.isExpandable(i));
			if (pNode.isExpandable(i)) {
				MappingNode child = new MappingNode(this, pNode, i, true);
				if (MathUtils.definitelyGreaterThan(child.lowerBound, minUpperBound)
						|| MathUtils.definitelyGreaterThan(child.cost, minCost)
						|| (MathUtils.approximatelyEqual(child.cost, minCost) && bestMapping != null)
						|| child.isIllegal()) {
					;
				} else {
//					System.out.println("Child upper upper bound is "
//							+ child.upperBound);
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
					if (child.stage == gProcNum) {
						minCost = child.cost;
						if (child.stage < gProcNum)
							minCost = child.upperBound;
						if (MathUtils.definitelyLessThan(minCost, minUpperBound))
							minUpperBound = minCost;
//						System.out
//								.println("Current minimum cost is " + minCost);
						bestMapping = child;
					} else {
						Q.insert(child);
						if (Q.length() >= priorityQueueSize && !insertAllFlag) {
							previousInsert = i;
							selectiveInsert(pNode, Q);
						}
					}
				}
			}
		}
	}

	private void selectiveInsert(MappingNode pNode, PriorityQueue Q) {
//		System.out.println("selectiveInsert " + MappingNode.cnt + " " + Q.length());
		if ((MathUtils.approximatelyEqual(Math.abs(pNode.upperBound - minUpperBound), 0.01f))
				&& MathUtils.definitelyLessThan(minUpperBound, MAX_VALUE)
				&& minUpperBoundHitCount <= minHitThreshold) {
			minUpperBoundHitCount++;
			insertAll(pNode, Q);
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
				|| (MathUtils.approximatelyEqual(child.cost, minCost) && bestMapping != null))
			return;
		else {
			if (MathUtils.definitelyLessThan(child.upperBound, minUpperBound - 0.01f)) {
				// In this case, we should also insert other children
				insertAllFlag = true;
				insertAll(pNode, Q);
			}
			if (child.stage == gProcNum || MathUtils.approximatelyEqual(child.lowerBound, child.upperBound)) {
				minCost = child.cost;
				if (child.stage < gProcNum)
					minCost = child.upperBound;
				if (MathUtils.definitelyLessThan(minCost, minUpperBound))
					minUpperBound = minCost;
				System.out.println("Current minimum cost is " + minCost);
				bestMapping = child;
			} else
				Q.insert(child);
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
			System.err.println("Error in expanding at stage "
					+ pNode.getStage());
			System.err.println("index = " + index);
			System.exit(-1);
		}
		child = new MappingNode(this, pNode, index, true);
		if (MathUtils.definitelyGreaterThan(child.lowerBound, minUpperBound)
				|| MathUtils.definitelyGreaterThan(child.cost, minCost))
			return;
		else {
			if (MathUtils.definitelyLessThan(child.upperBound, minUpperBound)) {
				minUpperBound = child.upperBound;
				System.out.println("Current minimum cost upper bound is "
						+ minUpperBound);
				minUpperBoundHitCount = 0;
			}
			if (child.stage == gProcNum
					|| MathUtils.approximatelyEqual(child.lowerBound, child.upperBound)) {
				if (MathUtils.approximatelyEqual(minCost, child.cost) && bestMapping != null)
					return;
				else {
					minCost = child.cost;
					if (child.stage < gProcNum)
						minCost = child.upperBound;
					if (MathUtils.definitelyLessThan(minCost, minUpperBound))
						minUpperBound = minCost;
					System.out.println("Current minimum cost is " + minCost);
					bestMapping = child;
				}
			} else
				Q.insert(child);
		}
	}

	private void applyMapping(MappingNode bestMapping) {
		for (int i = 0; i < gProcNum; i++)
			gProcess[i].setTileId(-1);
		for (int i = 0; i < gTileNum; i++)
			gTile[i].setProcId(-1);
		for (int i = 0; i < gProcNum; i++) {
			int procId = procMapArray[i];
			gProcess[procId].setTileId(bestMapping.mapToTile(i));
			gTile[bestMapping.mapToTile(i)].setProcId(procId);
		}
		if (buildRoutingTable)
			bestMapping.programRouters();
	}

	@Override
	public String map() throws TooFewNocNodesException {
		if (gTileNum < gProcNum) {
			throw new TooFewNocNodesException(gProcNum, gTileNum);
		}

		mapCoresToNocNodesRandomly();

		printCurrentMapping();

		long start = System.currentTimeMillis();
		System.out.println("Start mapping...");

		assert (gProcNum == ((int) gProcess.length));

		branchAndBoundMapping();
		long end = System.currentTimeMillis();
		System.out.println("Mapping process finished successfully.");
		System.out.println("Time: " + (end - start) / 1000 + " seconds");

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
					e.printStackTrace();
				}
				// System.err.print("ID = " + id);
			}

			if (line.contains("packet_to_destination_rate")) {
				String substring = line.substring(
						"packet_to_destination_rate".length() + 1).trim();
				int dstId = -1;
				try {
					dstId = Integer.valueOf(substring.substring(0,
							substring.indexOf("\t")));
					// System.err.print(" dst ID = " + dstId);
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
				double rate = 0;
				try {
					rate = Double.valueOf(substring.substring(substring
							.indexOf("\t") + 1));
					// System.err.print(" rate = " + rate);
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}

				if (rate > 1) {
					System.err.println("Invalid rate!");
					System.exit(0);
				}
				gProcess[id].getToCommunication()[dstId] = (int) (rate * 1000000);
				gProcess[id].getToBandwidthRequirement()[dstId] = (int) (rate * 3 * linkBandwidth);
				gProcess[dstId].getFromCommunication()[id] = (int) (rate * 1000000);
				gProcess[dstId].getFromBandwidthRequirement()[id] = (int) (rate * 3 * linkBandwidth);
				// System.err.println();
			}
		}

		br.close();
	}

	private boolean verifyBandwidthRequirement() {
		generateLinkUsageList();

	    for (int i=0; i<gLinkNum; i++) 
	        gLink[i].setUsedBandwidth(0);

	    for (int src=0; src<gTileNum; src++) {
	        for (int dst=0; dst<gTileNum; dst++) {
	            if (src == dst)
	                continue;
	            int src_proc = gTile[src].getProcId();
	            int dst_proc = gTile[dst].getProcId();
	            int comm_load = gProcess[src_proc].getToBandwidthRequirement()[dst_proc];
	            if (comm_load == 0)
	                continue;
	            Tile current_tile = gTile[src];
	            while (current_tile.getTileId() != dst) {
	                int link_id = current_tile.routeToLink(src, dst);
	                Link pL = gLink[link_id];
	                current_tile = gTile[pL.getToTileId()];
	                gLink[link_id].setUsedBandwidth(gLink[link_id].getUsedBandwidth() + comm_load);
	            }
	        }
	    }
	    //check for the overloaded links
	    int violations = 0;
	    for (int i=0; i<gLinkNum; i++) {
	        if (gLink[i].getUsedBandwidth()> gLink[i].getBandwidth()) {
	        	System.out.println("Link " + i + " is overloaded: " + gLink[i].getUsedBandwidth() + " > "
	                 + gLink[i].getBandwidth());
	            violations ++;
	        }
	    }
	    if (violations > 0)
	        return false;
	    return true;
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
//		System.out.println("switch energy " + switchEnergy);
//		System.out.println("link energy " + linkEnergy);
//		System.out.println("buffer energy " + bufferEnergy);
		return switchEnergy + linkEnergy + bufferEnergy;
	}
	
	private float calculateSwitchEnergy() {
		float energy = 0;
		for (int src = 0; src < gTileNum; src++) {
			for (int dst = 0; dst < gTileNum; dst++) {
				int srcProc = gTile[src].getProcId();
				int dstProc = gTile[dst].getProcId();
				int commVol = gProcess[srcProc].getToCommunication()[dstProc];
				if (commVol > 0) {
					energy += gTile[src].getCost() * commVol;
					Tile currentTile = gTile[src];
//					 System.out.println("adding " + currentTile.getCost()
//					 + " * " + commVol + " (core " + srcProc
//					 + " to core " + dstProc + ") current tile "
//					 + currentTile.getTileId());
					while (currentTile.getTileId() != dst) {
						int linkId = currentTile.getRoutingEntries()[src][dst];
						currentTile = gTile[gLink[linkId].getToTileId()];
						energy += currentTile.getCost() * commVol;
//						 System.out.println("adding " + currentTile.getCost()
//						 + " * " + commVol + " (core " + srcProc
//						 + " to core " + dstProc + ") current tile "
//						 + currentTile.getTileId() + " link ID " + linkId);
					}
				}
			}
		}
		return energy;
	}

	private float calculateLinkEnergy() {
		float energy = 0;
		for (int src = 0; src < gTileNum; src++) {
			for (int dst = 0; dst < gTileNum; dst++) {
				int srcProc = gTile[src].getProcId();
				int dstProc = gTile[dst].getProcId();
				int commVol = gProcess[srcProc].getToCommunication()[dstProc];
				if (commVol > 0) {
					Tile currentTile = gTile[src];
					while (currentTile.getTileId() != dst) {
						int linkId = currentTile.getRoutingEntries()[src][dst];
						energy += gLink[linkId].getCost() * commVol;
						currentTile = gTile[gLink[linkId].getToTileId()];
					}
				}
			}
		}
		return energy;
	}

	private float calculateBufferEnergy() {
		float energy = 0;
		for (int src = 0; src < gTileNum; src++) {
			for (int dst = 0; dst < gTileNum; dst++) {
				int srcProc = gTile[src].getProcId();
				int dstProc = gTile[dst].getProcId();
				int commVol = gProcess[srcProc].getToCommunication()[dstProc];
				if (commVol > 0) {
					Tile currentTile = gTile[src];
					while (currentTile.getTileId() != dst) {
						int linkId = currentTile.getRoutingEntries()[src][dst];
						energy += (bufReadEBit + bufWriteEBit) * commVol;
						currentTile = gTile[gLink[linkId].getToTileId()];
					}
					energy += bufWriteEBit * commVol;
				}
			}
		}
		return energy;
	}
	
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
			IOException {
		if (args == null || args.length < 1) {
			System.err.println("usage: BranchAndBoundMapper {routing}");
			System.err
					.println("(where routing may be true or false; any other value means false)");
		} else {
			// from the initial random mapping, I think tiles must equal cores
			// (it
			// is not enough to have cores <= tiles)
			int tiles = 16;
			int cores = 16;
			int linkBandwidth = 1000000;
			int priorityQueueSize = 2000;
			float switchEBit = 0.284f;
			float linkEBit = 0.449f;
			float bufReadEBit = 1.056f;
			float bufWriteEBit = 2.831f;

			BranchAndBoundMapper bbMapper;
			if ("true".equals(args[0])) {
				// Branch and Bound with routing
				bbMapper = new BranchAndBoundMapper(tiles, cores,
						linkBandwidth, priorityQueueSize, true,
						LegalTurnSet.ODD_EVEN, bufReadEBit, bufWriteEBit);
			} else {
				// Branch and Bound without routing
				bbMapper = new BranchAndBoundMapper(tiles, cores,
						linkBandwidth, priorityQueueSize, bufReadEBit, bufWriteEBit);
			}

			bbMapper.initializeCores();
			bbMapper.initializeNocTopology(switchEBit, linkEBit);

			bbMapper.parseTrafficConfig(
					"telecom-mocsyn-16tile-selectedpe.traffic.config",
					linkBandwidth);

			bbMapper.map();

			bbMapper.printCurrentMapping();
			
			bbMapper.analyzeIt();
		}
	}

}
