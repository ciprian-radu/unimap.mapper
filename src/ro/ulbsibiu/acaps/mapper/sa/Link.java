package ro.ulbsibiu.acaps.mapper.sa;

/**
 * Holds data regarding a communication channel from a NoC
 * 
 * @author cipi
 * 
 */
public class Link {

	/** the ID of the link */
	private int linkId = -1;

	/** bandwidth's link */
	private int bandwidth = -1;

	/** the ID of the tile from which traffic is sent through this link */
	private int fromTileId;
	
	private int fromTileRow;
	
	private int fromTileColumn;

	/** the ID of the tile to which traffic is sent through this link */
	private int toTileId;
	
	private int toTileRow;
	
	private int toTileColumn;

	private float cost;
	
	private int usedBandwidth = 0;

	public Link(int linkId, int bandwidth, int fromTileId, int toTileId,
			float cost) {
		super();
		this.linkId = linkId;
		this.bandwidth = bandwidth;
		this.fromTileId = fromTileId;
		this.toTileId = toTileId;
		this.cost = cost;
	}

	public void setLinkId(int linkId) {
		this.linkId = linkId;
	}

	public void setBandwidth(int bandwidth) {
		this.bandwidth = bandwidth;
	}

	public int getLinkId() {
		return linkId;
	}

	public int getBandwidth() {
		return bandwidth;
	}

	public int getFromTileId() {
		return fromTileId;
	}

	public void setFromTileId(int fromTileId) {
		this.fromTileId = fromTileId;
	}

	public int getFromTileRow() {
		return fromTileRow;
	}

	public void setFromTileRow(int fromTileRow) {
		this.fromTileRow = fromTileRow;
	}

	public int getFromTileColumn() {
		return fromTileColumn;
	}

	public void setFromTileColumn(int fromTileColumn) {
		this.fromTileColumn = fromTileColumn;
	}

	public int getToTileId() {
		return toTileId;
	}

	public int getToTileRow() {
		return toTileRow;
	}

	public void setToTileRow(int toTileRow) {
		this.toTileRow = toTileRow;
	}

	public int getToTileColumn() {
		return toTileColumn;
	}

	public void setToTileColumn(int toTileColumn) {
		this.toTileColumn = toTileColumn;
	}

	public void setToTileId(int toTileId) {
		this.toTileId = toTileId;
	}

	public float getCost() {
		return cost;
	}

	public void setCost(float cost) {
		this.cost = cost;
	}

	public int getUsedBandwidth() {
		return usedBandwidth;
	}

	public void setUsedBandwidth(int usedBandwidth) {
		this.usedBandwidth = usedBandwidth;
	}

}