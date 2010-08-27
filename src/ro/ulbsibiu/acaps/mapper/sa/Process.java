package ro.ulbsibiu.acaps.mapper.sa;

/**
 * Holds data regarding a process attached to a NoC tile. Note that each process
 * is uniquely assigned to an IP core.
 * 
 * @author cipi
 * 
 */
public class Process {

	/** the ID of this process */
	private int procId = -1;

	/** the ID of the NoC tile to which this process is mapped to */
	private int tileId = -1;

	private int[] toCommunication = null;

	private int[] fromCommunication = null;

	/** the bandwidth requirement of the out-going traffic */
	private int[] toBandwidthRequirement = null;

	/** the bandwidth requirement of the incoming traffic */
	private int[] fromBandwidthRequirement = null;

	private int rank;
	
	private int totalCommVol;
	
	public Process(int procId, int tileId) {
		super();
		this.procId = procId;
		this.tileId = tileId;
	}

	public int getProcId() {
		return procId;
	}

	public void setProcId(int procId) {
		this.procId = procId;
	}

	public int getTileId() {
		return tileId;
	}

	public void setTileId(int tileId) {
		this.tileId = tileId;
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