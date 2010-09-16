package ro.ulbsibiu.acaps.mapper.sa;

/**
 * Holds data regarding a core attached to a NoC node. Note that each core
 * is uniquely assigned to an IP core.
 * 
 * @author cipi
 * 
 */
public class Core {

	/** the ID of this core */
	private int coreId = -1;

	/** the ID of the NoC node to which this core is mapped to */
	private int nodeId = -1;

	/** the volumes of communication which will be sent by this core */
	private int[] toCommunication = null;

	/** the volumes of communication which will be received by this core */
	private int[] fromCommunication = null;

	/** the bandwidth requirement of the out-going traffic */
	private int[] toBandwidthRequirement = null;

	/** the bandwidth requirement of the incoming traffic */
	private int[] fromBandwidthRequirement = null;

	/** a rank may be used at ordering the cores (by different criteria) */
	private int rank = -1;
	
	/** the total communication volume */
	private int totalCommVol;
	
	/**
	 * Constructor
	 * 
	 * @param coreId
	 *            the ID of the core
	 * @param nodeId
	 *            the ID of the node to which this core is assigned to
	 */
	public Core(int coreId, int nodeId) {
		super();
		this.coreId = coreId;
		this.nodeId = nodeId;
	}

	public int getCoreId() {
		return coreId;
	}

	public void setCoreId(int coreId) {
		this.coreId = coreId;
	}

	public int getNodeId() {
		return nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	public int[] getToCommunication() {
		return toCommunication;
	}

	public void setToCommunication(int[] toCommunication) {
		this.toCommunication = toCommunication;
	}

	public int[] getFromCommunication() {
		return fromCommunication;
	}

	public void setFromCommunication(int[] fromCommunication) {
		this.fromCommunication = fromCommunication;
	}

	public int[] getToBandwidthRequirement() {
		return toBandwidthRequirement;
	}

	public void setToBandwidthRequirement(int[] toBandwidthRequirement) {
		this.toBandwidthRequirement = toBandwidthRequirement;
	}

	public int[] getFromBandwidthRequirement() {
		return fromBandwidthRequirement;
	}

	public void setFromBandwidthRequirement(int[] fromBandwidthRequirement) {
		this.fromBandwidthRequirement = fromBandwidthRequirement;
	}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}

	public int getTotalCommVol() {
		return totalCommVol;
	}

	public void setTotalCommVol(int totalCommVol) {
		this.totalCommVol = totalCommVol;
	}

}