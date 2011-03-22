package ro.ulbsibiu.acaps.mapper.ga;

/**
 * @author shaikat
 *
 */
public class Core {

	/** the Number of this core  (1, 2, 3, ......, N)*/
	
	private int coreNo = -1;
	
	/** the ID of the APCG to which this cores belongs to */
	private String apcgId = null;

	/** the ID of the NoC node to which this core is mapped to */
	private int nodeId = -1;
	
	/**the UId of the core*/
	private String coreUid;
	
	public Core(int coreNo, String apcgId, int nodeId) {
		this.coreNo = coreNo;
		this.apcgId = apcgId;
		this.nodeId = nodeId;
	}

	public int getCoreNo() {
		return coreNo;
	}

	public void setCoreNo(int coreNo) {
		this.coreNo = coreNo;
	}

	public String getCoreUid() {
		return coreUid;
	}

	public void setCoreUid(String coreUid) {
		this.coreUid = coreUid;
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
}
