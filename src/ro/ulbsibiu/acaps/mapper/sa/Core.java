package ro.ulbsibiu.acaps.mapper.sa;

/**
 * Holds data regarding a core attached to a NoC node. Note that each core has
 * only one task assigned to it (thus, core is synonym to process/task, in this
 * context).
 * 
 * @author cipi
 * 
 */
public class Core {

	/** the ID of this core */
	private int coreId = -1;
	
	/** the ID of the APCG to which this cores belongs to */
	private String apcgId = null;

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
	 * @param apcgId
	 *            the ID of the APCG to which this cores belongs to
	 * @param nodeId
	 *            the ID of the node to which this core is assigned to
	 */
	public Core(int coreId, String apcgId, int nodeId) {
		this.coreId = coreId;
		this.apcgId = apcgId;
		this.nodeId = nodeId;
	}

	public int getCoreId() {
		return coreId;
	}

	public void setCoreId(int coreId) {
		this.coreId = coreId;
	}

	public String getApcgId() {
		return apcgId;
	}

	public void setApcgId(String apcgId) {
		this.apcgId = apcgId;
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