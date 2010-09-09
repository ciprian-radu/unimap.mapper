package ro.ulbsibiu.acaps.mapper.sa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds data regarding a Network-on-Chip tile
 * 
 * @author cipi
 * 
 */
public class Tile {

	/** the ID of this NoC tile */
	private int tileId = -1;

	/** the ID of the process mapped to this NoC tile */
	private int procId = -1;

	/** the 2D mesh row of this tile */
	private int row;

	/** the 2D mesh column of this tile */
	private int column;

	/** a list with the IDs of link that enter this tile */
	private List<Integer> inLinkList;

	/** a list with the IDs of link that exit this tile */
	private List<Integer> outLinkList;

	/**
	 * routingTable[i][j] shows how to which link to send data from tile i to
	 * tile j. If it's -2, means not reachable. If it's -1, means the
	 * destination is the current tile.
	 */
	private int[][] routingTable;

	private float cost;

	public Tile(int tileId, int procId, int row, int column, float cost) {
		super();
		this.tileId = tileId;
		this.procId = procId;
		this.row = row;
		this.column = column;
		this.cost = cost;
		inLinkList = new ArrayList<Integer>();
		outLinkList = new ArrayList<Integer>();
	}

	public void setTileId(int tileId) {
		this.tileId = tileId;
	}

	public void setProcId(int procId) {
		this.procId = procId;
	}

	public int getTileId() {
		return tileId;
	}

	public int getProcId() {
		return procId;
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
		inLinkList.add(linkId);
	}

	public List<Integer> getInLinkList() {
		return inLinkList;
	}

	public void addOutLink(int linkId) {
		outLinkList.add(linkId);
	}

	public List<Integer> getOutLinkList() {
		return outLinkList;
	}

	public void generateXYRoutingTable(int gTileNum, int gEdgeSize, Link[] gLink) {
		routingTable = new int[gTileNum][gTileNum];
		for (int i = 0; i < gTileNum; i++) {
			for (int j = 0; j < gTileNum; j++) {
				routingTable[i][j] = -2;
			}
		}

		for (int dstTile = 0; dstTile < gTileNum; dstTile++) {
			if (dstTile == tileId) { // deliver to me
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

			for (int i = 0; i < outLinkList.size(); i++) {
				if (gLink[outLinkList.get(i)].getToTileRow() == nextStepRow
						&& gLink[outLinkList.get(i)].getToTileColumn() == nextStepCol) {
					routingTable[0][dstTile] = gLink[outLinkList.get(i)]
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
	 * Accesses the routing table of of this tile in order to retrieve the ID of
	 * the link used for routing from src to dst.
	 * 
	 * @param src
	 *            the ID of the source tile
	 * @param dst
	 *            the ID of the destination tile
	 * @return the ID of the link used for this routing
	 */
	public int routeToLink(int src, int dst) {
		return routingTable[src][dst];
	}
}