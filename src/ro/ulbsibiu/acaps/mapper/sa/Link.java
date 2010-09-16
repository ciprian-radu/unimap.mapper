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

	/** link's bandwidth */
	private int bandwidth = -1;

	/** the ID of the tile from which traffic is sent through this link */
	private int fromNodeId;
	
	private int fromNodeRow;
	
	private int fromNodeColumn;

	/** the ID of the tile to which traffic is sent through this link */
	private int toNodeId;
	
	private int toNodeRow;
	
	private int toNodeColumn;

	/**
	 * A cost attached to this link. It may for example be something like energy
	 * consumption.
	 */
	private float cost;
	
	private int usedBandwidth = 0;

	/**
	 * Constructor
	 * 
	 * @param linkId
	 *            the ID of the link
	 * @param bandwidth
	 *            the bandwidth
	 * @param fromNodeId
	 *            from what tile this link transmits data
	 * @param toNodeId
	 *            to what tile this link transmits data
	 * @param cost
	 *            the cost attached to this link (it may for example be
	 *            something like energy consumption)
	 */
	public Link(int linkId, int bandwidth, int fromNodeId, int toNodeId,
			float cost) {
		super();
		this.linkId = linkId;
		this.bandwidth = bandwidth;
		this.fromNodeId = fromNodeId;
		this.toNodeId = toNodeId;
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

	public int getFromNodeId() {
		return fromNodeId;
	}

	public void setFromNodeId(int fromNodeId) {
		this.fromNodeId = fromNodeId;
	}

	public int getFromNodeRow() {
		return fromNodeRow;
	}

	public void setFromNodeRow(int fromNodeRow) {
		this.fromNodeRow = fromNodeRow;
	}

	public int getFromNodeColumn() {
		return fromNodeColumn;
	}

	public void setFromNodeColumn(int fromNodeColumn) {
		this.fromNodeColumn = fromNodeColumn;
	}

	public int getToTileId() {
		return toNodeId;
	}

	public int getToTileRow() {
		return toNodeRow;
	}

	public void setToNodeRow(int toNodeRow) {
		this.toNodeRow = toNodeRow;
	}

	public int getToNodeColumn() {
		return toNodeColumn;
	}

	public void setToNodeColumn(int toNodeColumn) {
		this.toNodeColumn = toNodeColumn;
	}

	public void setToNodeId(int toNodeId) {
		this.toNodeId = toNodeId;
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