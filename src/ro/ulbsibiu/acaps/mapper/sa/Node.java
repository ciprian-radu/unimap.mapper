package ro.ulbsibiu.acaps.mapper.sa;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds data regarding a Network-on-Chip node
 * 
 * @author cipi
 * 
 */
public class Node {

	/** the ID of this NoC node */
	private int nodeId = -1;

	/** the ID of the core mapped to this NoC node */
	private int coreId = -1;

	/** the 2D mesh row of this node */
	private int row;

	/** the 2D mesh column of this node */
	private int column;

	/** a list with the IDs of link that enter this node */
	private List<Integer> inLinks;

	/** a list with the IDs of link that exit this node */
	private List<Integer> outLinks;

	/**
	 * routingTable[i][j] shows the ID of the link to which data must be sent
	 * from node i, so that it reaches node j. If it's -2, means not reachable.
	 * If it's -1, means the destination is the current node.
	 */
	private int[][] routingTable;

	/**
	 * A cost attached to this node. It may for example be something like energy
	 * consumption.
	 */
	private float cost;

	/**
	 * Constructor
	 * 
	 * @param nodeId
	 *            the ID of this node
	 * @param coreId
	 *            the ID of the core mapped to this node
	 * @param row
	 * @param column
	 * @param cost
	 *            the cost of this node (it may for example be something like
	 *            energy consumption)
	 */
	public Node(int nodeId, int coreId, int row, int column, float cost) {
		super();
		this.nodeId = nodeId;
		this.coreId = coreId;
		this.row = row;
		this.column = column;
		this.cost = cost;
		inLinks = new ArrayList<Integer>();
		outLinks = new ArrayList<Integer>();
	}

	public void setTileId(int nodeId) {
		this.nodeId = nodeId;
	}

	public void setCoreId(int coreId) {
		this.coreId = coreId;
	}

	public int getTileId() {
		return nodeId;
	}

	public int getCoreId() {
		return coreId;
	}

	public void setRow(int row) {
		this.row = row;
	}

	public void setColumn(int column) {
		this.column = column;
	}

	public int getRow() {
		return row;
	}

	public int getColumn() {
		return column;
	}

	public void setRoutingEntry(int srcTileId, int dstTileId, int linkId) {
		routingTable[srcTileId][dstTileId] = linkId;
	}

	/**
	 * @return the routing table
	 */
	public int[][] getRoutingEntries() {
		return routingTable;
	}

	public float getCost() {
		return cost;
	}

	public void setCost(float cost) {
		this.cost = cost;
	}

	public void addInLink(int linkId) {
		inLinks.add(linkId);
	}

	public List<Integer> getInLinks() {
		return inLinks;
	}

	public void addOutLink(int linkId) {
		outLinks.add(linkId);
	}

	public List<Integer> getOutLinks() {
		return outLinks;
	}

	public void generateXYRoutingTable(int gTileNum, int gEdgeSize, Link[] gLink) {
		routingTable = new int[gTileNum][gTileNum];
		for (int i = 0; i < gTileNum; i++) {
			for (int j = 0; j < gTileNum; j++) {
				routingTable[i][j] = -2;
			}
		}

		for (int dstTile = 0; dstTile < gTileNum; dstTile++) {
			if (dstTile == nodeId) { // deliver to me
				routingTable[0][dstTile] = -1;
				continue;
			}

			// check out the dst Tile's position first
			int dstRow = dstTile / gEdgeSize;
			int dstCol = dstTile % gEdgeSize;

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

			for (int i = 0; i < outLinks.size(); i++) {
				if (gLink[outLinks.get(i)].getToTileRow() == nextStepRow
						&& gLink[outLinks.get(i)].getToNodeColumn() == nextStepCol) {
					routingTable[0][dstTile] = gLink[outLinks.get(i)]
							.getLinkId();
					break;
				}
			}
		}

		// Duplicate this routing row to the other routing rows.
		for (int i = 1; i < gTileNum; i++) {
			for (int j = 0; j < gTileNum; j++) {
				routingTable[i][j] = routingTable[0][j];
			}
		}
	}

	/**
	 * Accesses the routing table of of this node in order to retrieve the ID of
	 * the link used for routing from src to dst.
	 * 
	 * @param src
	 *            the ID of the source node
	 * @param dst
	 *            the ID of the destination node
	 * @return the ID of the link used for this routing
	 */
	public int routeToLink(int src, int dst) {
		return routingTable[src][dst];
	}
}